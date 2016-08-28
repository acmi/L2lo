/*
 * Copyright (c) 2016 acmi
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package acmi.l2.clientmod.l2lo;

import acmi.l2.clientmod.io.UnrealPackage;
import acmi.util.AutoCompleteComboBox;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileFilter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static acmi.l2.clientmod.io.BufferUtil.getCompactInt;
import static javafx.scene.input.KeyCombination.keyCombination;

public class Controller implements Initializable {
    private static final boolean SHOW_STACKTRACE = System.getProperty("L2lo.showStackTrace", "false").equalsIgnoreCase("true");
    public static final FileFilter MAP_FILE_FILTER = pathname ->
            (pathname != null) && (pathname.isFile()) && (pathname.getName().endsWith(".unr"));

    @FXML
    private TextField l2Path;
    @FXML
    private ComboBox<String> unrChooser;
    @FXML
    private ListView<UnrealPackage.Entry> list;
    @FXML
    private ComboBox<UnrealPackage.Entry> add;
    @FXML
    private ProgressIndicator progress;

    private ObjectProperty<Stage> stage = new SimpleObjectProperty<>();

    private final ObjectProperty<File> l2Dir = new SimpleObjectProperty<>();
    private final ObjectProperty<File> mapsDir = new SimpleObjectProperty<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "L2lo Executor") {{
        setDaemon(true);
    }});

    public Stage getStage() {
        return stage.get();
    }

    public ObjectProperty<Stage> stageProperty() {
        return stage;
    }

    public void setStage(Stage stage) {
        this.stage.set(stage);
    }

    public File getL2Dir() {
        return l2Dir.get();
    }

    public ObjectProperty<File> l2DirProperty() {
        return l2Dir;
    }

    public void setL2Dir(File l2Dir) {
        this.l2Dir.set(l2Dir);
    }

    public File getMapsDir() {
        return mapsDir.get();
    }

    public ReadOnlyObjectProperty<File> mapsDirProperty() {
        return mapsDir;
    }

    public Controller() {
        mapsDir.bind(Bindings.createObjectBinding(() -> Util.find(getL2Dir(), File::isDirectory, Util.nameFilter("maps")), l2DirProperty()));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        stageProperty().addListener(observable -> initializeKeyCombinations());

        this.l2Path.textProperty().bind(Bindings
                .when(l2DirProperty().isNotNull())
                .then(Bindings.convert(l2DirProperty()))
                .otherwise(""));


        mapsDirProperty().addListener((observable, oldValue, newValue) -> {
            unrChooser.getSelectionModel().clearSelection();
            unrChooser.getItems().clear();
            unrChooser.setDisable(true);

            if (newValue == null)
                return;

            unrChooser.getItems().addAll(Arrays
                    .stream(Optional.ofNullable(newValue.listFiles(MAP_FILE_FILTER)).orElse(new File[]{}))
                    .map(File::getName)
                    .collect(Collectors.toList()));

            unrChooser.setDisable(false);

            AutoCompleteComboBox.autoCompleteComboBox(unrChooser, AutoCompleteComboBox.AutoCompleteMode.CONTAINING);
        });
        this.unrChooser.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            System.out.println(newValue);

            System.gc();

            if (newValue == null)
                return;

            try (UnrealPackage up = new UnrealPackage(new File(getMapsDir(), newValue), true)) {
                longTask(progress -> {
                    UnrealPackage.ExportEntry entry = up.getExportTable()
                            .parallelStream()
                            .filter(e -> e.getObjectName().getName().equalsIgnoreCase("myLevel"))
                            .filter(e -> e.getFullClassName().equalsIgnoreCase("Engine.Level"))
                            .findAny()
                            .orElseThrow(() -> new IllegalStateException("myLevel[Engine.Level] not found"));

                    ByteBuffer buffer = ByteBuffer.wrap(entry.getObjectRawDataExternally())
                            .order(ByteOrder.LITTLE_ENDIAN);
                    getCompactInt(buffer);

                    int asoSize = buffer.getInt();
                    buffer.getInt();
                    List<UnrealPackage.Entry> objList = new ArrayList<>(asoSize);
                    for (int i = 0; i < asoSize; i++) {
                        int ref = getCompactInt(buffer);
                        if (ref == 0)
                            objList.add(new UnrealPackage.Entry(up, 0, 0, 0) {
                                @Override
                                public String getFullClassName() {
                                    return null;
                                }

                                @Override
                                public int getObjectReference() {
                                    return 0;
                                }

                                @Override
                                public List getTable() {
                                    return null;
                                }
                            });
                        else
                            objList.add(up.objectReference(ref));
                    }

                    int objSize = buffer.getInt();
                    buffer.getInt();
                    for (int i = 0; i < objSize; i++)
                        objList.add(up.objectReference(getCompactInt(buffer)));

                    list.getItems().setAll(objList);
                }, e -> onException("Import failed", e));
            }
        });

        list.setCellFactory(param -> new ListCell<UnrealPackage.Entry>() {
            private Label text = new Label();
            private Button remove = new Button("DEL");

            private HBox graphic = new HBox(text, remove);
            {
                text.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(text, Priority.ALWAYS);
                graphic.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(UnrealPackage.Entry item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null)
                    setGraphic(null);
                else {
                    text.setText(item.toString());
                    remove.setOnAction(event -> list.getItems().remove(item));
                    setGraphic(graphic);
                }
            }
        });
    }

    private void initializeKeyCombinations() {
        Map<KeyCombination, Runnable> keyCombinations = new HashMap<>();
        keyCombinations.put(keyCombination("CTRL+O"), this::chooseL2Folder);

        getStage().getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> keyCombinations.entrySet()
                .stream()
                .filter(e -> e.getKey().match(event))
                .findAny()
                .ifPresent(e -> {
                    e.getValue().run();
                    event.consume();
                }));
    }

    public void chooseL2Folder() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select L2 folder");
        File dir = directoryChooser.showDialog(getStage());
        if (dir == null)
            return;

        setL2Dir(dir);
    }

    protected interface Task {
        void run(Consumer<Double> progress) throws Exception;
    }

    protected void longTask(Task task, Consumer<Throwable> exceptionHandler) {
        executor.execute(() -> {
            Platform.runLater(() -> {
                progress.setProgress(-1);
                progress.setVisible(true);
            });

            try {
                task.run(value -> Platform.runLater(() -> progress.setProgress(value)));
            } catch (Throwable t) {
                exceptionHandler.accept(t);
            } finally {
                Platform.runLater(() -> progress.setVisible(false));
            }
        });
    }

    private void onException(String text, Throwable ex) {
        ex.printStackTrace();

        Platform.runLater(() -> {
            if (SHOW_STACKTRACE) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText(text);

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                ex.printStackTrace(pw);
                String exceptionText = sw.toString();

                Label label = new Label("Exception stacktrace:");

                TextArea textArea = new TextArea(exceptionText);
                textArea.setEditable(false);
                textArea.setWrapText(true);

                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setMaxHeight(Double.MAX_VALUE);
                GridPane.setVgrow(textArea, Priority.ALWAYS);
                GridPane.setHgrow(textArea, Priority.ALWAYS);

                GridPane expContent = new GridPane();
                expContent.setMaxWidth(Double.MAX_VALUE);
                expContent.add(label, 0, 0);
                expContent.add(textArea, 0, 1);

                alert.getDialogPane().setExpandableContent(expContent);

                alert.showAndWait();
            } else {
                //noinspection ThrowableResultOfMethodCallIgnored
                Throwable t = Util.getTop(ex);

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(t.getClass().getSimpleName());
                alert.setHeaderText(text);
                alert.setContentText(t.getMessage());

                alert.showAndWait();
            }
        });
    }
}

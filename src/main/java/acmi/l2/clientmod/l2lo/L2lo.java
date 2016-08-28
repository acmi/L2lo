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

import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.util.prefs.Preferences;

public class L2lo extends Application {
    private final ObjectProperty<File> l2Dir = new SimpleObjectProperty<>();

    public void start(Stage stage) throws Exception {
        loadConfig();

        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(L2lo.class.getResource("l2lo.fxml"));

        Parent root = loader.load();

        stage.setScene(new Scene(root));
        stage.titleProperty().bind(Bindings.createStringBinding(() ->
                (l2Dir.get() != null ? l2Dir.get().toString() + " - " : "") + "L2lo" , l2Dir));

        Controller controller = loader.getController();
        controller.l2DirProperty().bindBidirectional(l2Dir);
        controller.setStage(stage);

        stage.show();

        stage.setX(Double.parseDouble(getPrefs().get("l2lo.x", String.valueOf(stage.getX()))));
        stage.setY(Double.parseDouble(getPrefs().get("l2lo.y", String.valueOf(stage.getY()))));
        stage.setWidth(Double.parseDouble(getPrefs().get("l2lo.width", String.valueOf(stage.getWidth()))));
        stage.setHeight(Double.parseDouble(getPrefs().get("l2lo.height", String.valueOf(stage.getHeight()))));

        InvalidationListener listener = observable -> {
            getPrefs().put("l2lo.x", String.valueOf(Math.round(stage.getX())));
            getPrefs().put("l2lo.y", String.valueOf(Math.round(stage.getY())));
            getPrefs().put("l2lo.width", String.valueOf(Math.round(stage.getWidth())));
            getPrefs().put("l2lo.height", String.valueOf(Math.round(stage.getHeight())));
        };
        stage.xProperty().addListener(listener);
        stage.yProperty().addListener(listener);
        stage.widthProperty().addListener(listener);
        stage.heightProperty().addListener(listener);
    }

    private void loadConfig() {
        try {
            l2Dir.set(new File(getPrefs().get("path.l2", null)));
        } catch (Exception ignore) {
        }

        l2Dir.addListener(observable -> {
            try {
                getPrefs().put("path.l2", l2Dir.get().getPath());
            } catch (Exception ignore) {
            }
        });
    }

    static Preferences getPrefs() {
        return Preferences.userRoot().node("l2lo");
    }

    public static void main(String[] args) {
        launch(args);
    }
}

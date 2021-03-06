/*

 */
package g710visualizer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javax.sound.sampled.*;
import com.logitech.gaming.LogiLED;
import java.util.ArrayList;
import java.util.Optional;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;

/**
 *
 * @author Bryan Hauser <vmkid95@gmail.com>
 */
public class G710Visualizer extends Application {
    
    TargetDataLine targetDataLine = null;
    int bufferSize=0;
    boolean runListener=false;
    boolean killSwitch=true;
    int dampener=0;
    int multiplier=1;
    //LogiLED keyboard = new LogiLED();
    
    @Override
    public void start(Stage primaryStage) {
        //Initialize logiLED
        try{
        LogiLED.LogiLedInit();
        }catch(Exception e){
            e.printStackTrace();
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error!");
            alert.setHeaderText("LogiLED Initialization Error");
            alert.setContentText("Could not initialize LogiLED. Is your keyboard plugged in?");
            alert.showAndWait();
            System.exit(3);
        }
        
        LogiLED.LogiLedSaveCurrentLighting();
        
        Mixer.Info[] info = AudioSystem.getMixerInfo();
        Mixer.Info selectedMixer = null;
        int selectedNumber=-1;
        int i=0;
        ArrayList<String> mixerChoices = new ArrayList();
        for (Mixer.Info print : info){
            System.out.println(i+": "+print.getName()+" is a "+print.getDescription());
            i++;
            if(print.getDescription().equals("Direct Audio Device: DirectSound Capture")){
                mixerChoices.add(print.getName());
            }
            //if(print.getName().startsWith("Stereo Mix")){
            //    selectedMixer = print;
            //    selectedNumber = i;
            //}
        }
        ChoiceDialog<String> mixerSelector = new ChoiceDialog("", mixerChoices);
        mixerSelector.setTitle("Logitech Sound Visualizer");
        mixerSelector.setHeaderText("Select your sound device.\n"
                + "Ensure the selected device is a \"Line-in\".\n"
                + "For best results, select Stereo Mix.");
        mixerSelector.setContentText("Select device:");
        
        Optional<String> chosenMixer = mixerSelector.showAndWait();
        if(chosenMixer.isPresent()){
            i=0;
            for(Mixer.Info choose : info){
                if(choose.getName().equals(chosenMixer.get())){
                    selectedMixer=choose;
                    selectedNumber=i;
                }
                i++;
            }
        }
        
        if(selectedMixer==null){
            /*System.out.println("No Stereo Mix mixer found.");
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error!");
            alert.setHeaderText("Stereo Mix Error");
            alert.setContentText("Could not find Stereo Mix. Enable Stereo Mix in your sound settings.");
            alert.showAndWait();*/
            System.exit(1);
        }
        System.out.println("Selecting mixer "+selectedNumber+": "+selectedMixer.getName());
        Mixer mixer = AudioSystem.getMixer(selectedMixer);
        
        System.out.println();
        Line.Info[] lineInfo = mixer.getTargetLineInfo();
        Line.Info selectedInfo = null;
        i=0;
        for(Line.Info print : lineInfo){
            System.out.println(i+": "+print.toString());
            selectedInfo=print;
        }
        
        
        try{
        targetDataLine = (TargetDataLine)mixer.getLine(selectedInfo);
        targetDataLine.open();
        bufferSize=targetDataLine.getBufferSize();
        }catch(Exception e){
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle("Error!");
            alert.setHeaderText("Audio Device Error");
            alert.setContentText("Could not open selected audio device.");
            alert.showAndWait();
            System.exit(1);
        }
        targetDataLine.flush();
        targetDataLine.start();
        System.out.println("Data mixer is "+targetDataLine.getLineInfo().toString());
        System.out.println("Buffer size is "+bufferSize);
        
        
        //Init the GUI
        Button startButton = new Button();
        Button stopButton = new Button();
        startButton.setText("Start");
        startButton.setOnAction((ActionEvent event) -> {
            if(runListener==false){
                LogiLED.LogiLedSaveCurrentLighting();
                System.out.println("Starting...");
                runListener=true;
                startButton.setDisable(true);
                stopButton.setDisable(false);
            }
            //repeatRate.scheduleAtFixedRate(updateLights, 0, 10);
        });
        stopButton.setText("Stop");
        stopButton.setOnAction((ActionEvent event) -> {
            if(runListener==true){
                System.out.println("Stopping...");
                runListener=false;
                LogiLED.LogiLedRestoreLighting();
                startButton.setDisable(false);
                stopButton.setDisable(true);
            }
        });
        stopButton.setDisable(true);
        
        Slider dampSlider = new Slider();
        dampSlider.valueProperty().addListener((ObservableValue<? extends Number> ov, Number old_damp, Number new_damp) -> {
            dampener=new_damp.intValue();
        });
        
        Label dampLabel = new Label("Dampener: ");
        
        Slider multiSlider = new Slider(1, 10, 1);
        multiSlider.valueProperty().addListener((ObservableValue<? extends Number> ov, Number old_mult, Number new_mult) -> {
            multiplier=new_mult.intValue();
        });
        
        Label multiLabel = new Label("Multiplier: ");
        
        
        VBox root = new VBox();
        HBox buttons = new HBox();
        HBox dampBox = new HBox();
        HBox multiBox = new HBox();
        buttons.getChildren().addAll(startButton, stopButton);
        dampBox.getChildren().addAll(dampLabel, dampSlider);
        multiBox.getChildren().addAll(multiLabel, multiSlider);
        root.getChildren().addAll(buttons, dampBox, multiBox);
        
        Scene scene = new Scene(root, 300, 250);
        
        
        primaryStage.setTitle("Logitech Sound Visualizer");
        primaryStage.setScene(scene);
        primaryStage.show();
        updateThread.start();
    }
    
    //Create the task
        
    Runnable updateLights = () -> {
        System.out.println("Thread started...");
        do{
            //System.out.println(killSwitch);
            byte buffer[] = new byte[100];
            targetDataLine.read(buffer, 0, 100);
            double rms = calculateRMSLevel(buffer);
            if(runListener){
                int num = (int)(Math.ceil(rms)-dampener)*multiplier;
                if (num<0){
                    num=0;
                }
                //System.out.println("Updating to "+num);
                LogiLED.LogiLedSetLighting(num, 0, 0);
            }
        }while(killSwitch);
    };
        
    Thread updateThread = new Thread(updateLights);


    public double calculateRMSLevel(byte[] audioData)
    { 
        long lSum = 0;
        for(int i=0; i < audioData.length; i++)
            lSum = lSum + audioData[i];

        double dAvg = lSum / audioData.length;
        double sumMeanSquare = 0d;

        for(int j=0; j < audioData.length; j++)
            sumMeanSquare += Math.pow(audioData[j] - dAvg, 2d);

        double averageMeanSquare = sumMeanSquare / audioData.length;

        //return (int)(Math.pow(averageMeanSquare,0.5d) + 0.5);
        return Math.pow(averageMeanSquare,0.5d);
    }
    
    @Override
  public void stop() throws Exception {
    killSwitch=false;
    runListener=false;
    targetDataLine.stop();
    targetDataLine.close();
    try{
        updateThread.join();
    }catch(Exception e){
    }
    LogiLED.LogiLedRestoreLighting();
    LogiLED.LogiLedShutdown();
    Platform.exit();
  }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
    
}

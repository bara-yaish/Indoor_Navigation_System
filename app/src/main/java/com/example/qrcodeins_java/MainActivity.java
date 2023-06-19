package com.example.qrcodeins_java;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private final int SPEECH_REQUEST_CODE = 102;
    private final String[] array = new String[]{"1dN1Ctc7cyXI9Jtzxgaf", "LmrK7Xc9gkHGWSqVW2Fr", "djIBnE3sUkIBryianlYJ", "LUCe6UOiq0pytT8KKRYz", "5lTVcMs8ictLQEW8DrPO"};
    //                                               Reception Area            Study Area            Multimedia Lab            Ricoh Area                 Toilet

    private String targetDestination = "";
    private TextToSpeech tts;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String phrase = "";
    private String decodedDataGlobal;
    private String previousPhrase = "No previous checkpoint scanned. Please scan and set a target destination.";
    private HashMap<String, String> checkpoints;
    private boolean isTargetSet = false;
    private boolean isGoingBack = false;
    private int currentIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button toggleScan = findViewById(R.id.btnScan);
        toggleScan.setOnClickListener(this);

        Button toggleReplay = findViewById(R.id.btnReplay);
        toggleReplay.setOnClickListener(this);

        Button toggleTargetDestination = findViewById(R.id.btnTargetDestination);
        toggleTargetDestination.setOnClickListener(this);

        setupTextToSpeech();
        getCheckpoints();
    }

    public void setupTextToSpeech() {
        tts = new TextToSpeech(getApplicationContext(),
                status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = tts.setLanguage(Locale.getDefault());

                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e("TTS", "Language not supported");
                        } else {
                            Toast.makeText(getApplicationContext(), "TTS Successfully Connected!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e("TTS", "Initialization Failed :( ");
                    }
        });
    }

    public void getCheckpoints() {
        db.collection("checkpoint")
                .get()
                .addOnCompleteListener(
                        task -> {
                            if (task.isSuccessful()) {
                                HashMap<String, String> tempStorage = new HashMap<>();
                                for (QueryDocumentSnapshot document : task.getResult()) {
                                    if (document.getData().size() == 1) {
                                        String temp = document.getString("description");
                                        tempStorage.put(document.getId(), temp);
                                    } else if (document.getData().size() == 2) {
                                        String temp = document.getString("location") + ". " + document.getString("description") + ".";
                                        tempStorage.put(document.getId(), temp);
                                    }
                                }
                                setCheckpoints(tempStorage);
                            } else {
                                Log.d(TAG, "Error getting documents: ", task.getException());
                            }
                });
    }

    private void setCheckpoints(HashMap<String, String> tempMap) {
        checkpoints = new HashMap<>(tempMap);

        for (Map.Entry<String, String> entry:
                checkpoints.entrySet()) {
            Log.d(TAG, entry.getKey() + ": " + entry.getValue());
        }
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.btnScan) {
            IntentIntegrator intentIntegrator = new IntentIntegrator(this);
            intentIntegrator.setOrientationLocked(false);
            intentIntegrator.setBeepEnabled(false);
            intentIntegrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
            intentIntegrator.setPrompt("");
            try {
                intentIntegrator.initiateScan();
            } catch (Exception e) {
                Log.e(TAG, "Task Failed", e);
            }

        }

        if (v.getId() == R.id.btnReplay) {
            tts.speak(previousPhrase, TextToSpeech.QUEUE_FLUSH, null, null);
        }

        if (v.getId() == R.id.btnTargetDestination) {
            String something = "";

            Log.d(TAG, "The set target destination: " + targetDestination);
//            try {
//                if (isTargetSet) {
//                    do {
//                        // CHECKPOINT IS NULL FOR SOME FREAKING REASON
//                        for (Map.Entry<String, String> entry
//                                : checkpoints.entrySet()) {
//                            if (entry.getKey().equals(targetDestination)) {
//                                something = entry.getValue();
//                                break;
//                            }
//                            // else, continue searching through
//                        }
//                    }while(something.isEmpty());
//
//                    Toast.makeText(this, "It has: \n" + something, Toast.LENGTH_SHORT).show();
//                } else {
//                    Toast.makeText(this, "Target is not set.", Toast.LENGTH_SHORT).show();
//                }
//            }catch (Exception e) {
//                e.printStackTrace();
//            }
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult intentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            assert data != null;
            String answer = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            confirmTargetDestination(answer);
        } else {
            if (intentResult != null) {
                String contents = intentResult.getContents();
                if (contents != null) {
                    vibrate();
                    evaluatePhrase();
                    fetchDocument(intentResult.getContents());
                } else {
                    Toast.makeText(getBaseContext(), "Cancelled", Toast.LENGTH_SHORT).show();
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    public void evaluatePhrase() {
        if (phrase.toLowerCase().contains("left path")) { return; }
        if (phrase.toLowerCase().contains("right path")) { return; }

        previousPhrase = phrase;
    }

    public void fetchDocument(String decodedData) {
        db.collection("checkpoint").document(decodedData).get()
                .addOnCompleteListener(
                        task -> {
                            if (task.isSuccessful()) {
                                DocumentSnapshot document = task.getResult();
                                if (document.exists()) {
                                    // If the retrieved document has only one field then it has to be
                                    // the left or right path qr code, otherwise it would be a normal checkpoint

                                    // We do that to phrase each properly since right/left path documents contain
                                    // only one field, unlike the rest of the documents.

                                    if (document.getData().size() == 1) {
                                        if (isGoingBack) {
                                            if (document.getString("description").toLowerCase().contains("please take the left path")) {
                                                phrase = "Please take the right path";
                                            }

                                            if (document.getString("description").toLowerCase().contains("please take the right path")) {
                                                phrase = "Please take the left path";
                                            }
                                        } else {
                                            phrase  = document.getString("description");
                                        }
                                    }

                                    if (document.getData().size() == 2) {
                                        phrase = document.getString("location") + ". " + document.getString("description") + ".";

                                        for (int i = 0; i < array.length; i++) {
                                            if (array[i].equals(decodedData)) {
                                                currentIndex = i;
                                            }
                                        }
                                    }

                                    decodedDataGlobal = decodedData;
                                    checkpointDetailsOutput();

                                    if (phrase.equalsIgnoreCase("")) {
                                        fetchDocument(decodedData);
                                    }
                                }
                            }
                });
    }

    private void checkpointDetailsOutput() {
        tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, null);

        // Try catch an exception in case the targetDestination is null before entering the if
        // statement. If it was null then ignore and ask for user input.

        // After reaching the target destination, the application will congratulate the user,
        // then set the targetDestination variable to null.

        // The decodedDataGlobal was set from the fetchDocument method. It sets the current
        // scanned decodedData that came from the results of the scanner intent.

        // The only possible reason that the application does not congratulate the user
        // upon reaching the destination is that the targetDestination is not equal to
        // the decodedDataGlobal for some reason. But which one of them is faulty?



        // If target is set then we can evaluate whether the user arrived or not.
        // If the user has arrived at target destination, the app should congratulate the user.
        // Else, then we check whether the scanned QR code is not left or right path codes.

        try {
        // START
            if (isTargetSet) {
                if (targetDestination.equals(decodedDataGlobal)) {
                    tts.speak("Congratulations, you have reached your destination.", TextToSpeech.QUEUE_ADD, null, null);
                    isTargetSet = false;
                    isGoingBack = true;
                    targetDestination = "";
                    return;
                } else {
                    if (phrase.toLowerCase().contains("left path") ||
                            phrase.toLowerCase().contains("right path")) {
                        return;
                    } else {
                        tts.speak("Please move forward.", TextToSpeech.QUEUE_ADD, null, null);
                    }
                }
            }
        // END
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }

        // How to say "Please move forward" successfully? And what are the conditions?

        // The only condition to say it is if the target is set AND the scanned QR code is not
        // one of those right or left path nonsense.
        // I believe that if the target is not set, then it should go thru anyways, so it is
        // reasonable to leave the second condition if the first one did not pass.
        // Now assuming the targetDestination is equal to decodedDataGlobal and the target is set,
        // that indicates that the user has arrived at his/her destination. Assuming that the
        // targetDestination is set and

        if (!isTargetSet) {
            switch(decodedDataGlobal) {
                case "6rOkhN8QGR9ufxdfq20H":
                case "lun9LF28b4j2pghaHCOh":
                    break;
                default:
                    askForUserInput();
            }
        }
    }

    private void askForUserInput() {
        tts.speak("What is your target destination?", TextToSpeech.QUEUE_ADD, null, null);
        getUserInput();
    }

    private void getUserInput() {

        while (tts.isSpeaking()) {
            // Pause the flow of the system
            try {
                Thread.sleep(1000);
//            android.os.SystemClock.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        startActivityForResult(intent, SPEECH_REQUEST_CODE);
    }

    private void confirmTargetDestination(String targetDes) {

        if(targetDes.toLowerCase().contains("study area")) {
            tts.speak("Target Destination Confirmed.", TextToSpeech.QUEUE_FLUSH, null, null);
            findOptimalPath(targetDes);
            return;
        }

        if(targetDes.toLowerCase().contains("toilet")) {
            tts.speak("Target Destination Confirmed.", TextToSpeech.QUEUE_FLUSH, null, null);
            findOptimalPath(targetDes);
            return;
        }

        if(targetDes.toLowerCase().contains("multimedia lab")) {
            tts.speak("Target Destination Confirmed.", TextToSpeech.QUEUE_FLUSH, null, null);
            findOptimalPath(targetDes);
            return;
        }

        if(targetDes.toLowerCase().contains("rico area")) {
            tts.speak("Target Destination Confirmed.", TextToSpeech.QUEUE_FLUSH, null, null);
            findOptimalPath(targetDes);
            return;
        }

        if(targetDes.toLowerCase().contains("reception area")) {
            tts.speak("Target Destination Confirmed.", TextToSpeech.QUEUE_FLUSH, null, null);
            findOptimalPath(targetDes);
            return;
        }

        tts.speak("Invalid Target Destination. Please input your target destination again.", TextToSpeech.QUEUE_FLUSH, null, null);

        while (tts.isSpeaking()) {
//             Pause the flow of the system
            try {
                Thread.sleep(500);
//                android.os.SystemClock.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        getUserInput();
    }

//  ==============================================================================================================
//  HHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH
//  ==============================================================================================================

    public void findOptimalPath(String td) {

        // If the variable targetDestination is not empty, that means the user has set a target.
        // In order to check the targetDestination is reached, we need to check if the targetDestination
        // is equal to the current decoded information.

        // The decoded information consists of random series of characters and targetDestination is a String.
        // Do I just convert the targetDestination to the series of characters or do something else?

        // The checkpoint hashmap has the series of characters as 'key' and the information to be relayed to
        // the user as 'value'.

        // Lets assume that converting will work. We will convert the targetDestination if it is not empty
        // into the corresponding series of characters to make it relevant to the scanned decoded data.
        // To do that, we need to loop thru each entry and ask if the targetDestination is in one of those
        // entries' values in which will then assign the key to the targetDestination variable.

        targetDestination = td;

        for (Map.Entry<String, String> entry
                : checkpoints.entrySet()) {
            if (entry.getValue().toLowerCase().contains(targetDestination.toLowerCase())) {
                targetDestination = entry.getKey();
            }
            // else, continue searching through
        }

        isTargetSet = true;
        // Now that we got the targetDestination in store, I suppose we can compare it with scanned code to
        // make sure whether the target destination is reached or not.

        int targetIndex = 0;
        for (int j = 0; j < array.length; j++) {
            if (array[j].equals(targetDestination)) {
                targetIndex = j;
            }
        }

        isGoingBack = currentIndex > targetIndex;

        if (isGoingBack) {
            tts.speak("Please move backward.", TextToSpeech.QUEUE_ADD, null, null);
        } else {
            tts.speak("Please move forward.", TextToSpeech.QUEUE_ADD, null, null);
        }
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(
                    VibrationEffect.createOneShot(600, VibrationEffect.DEFAULT_AMPLITUDE)
            );
        } else {
            vibrator.vibrate(600);
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
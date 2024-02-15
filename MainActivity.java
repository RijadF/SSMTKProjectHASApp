
package com.example.exonetlog77765;


// START LOG FILE IMPORTS
import android.annotation.SuppressLint;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
// END LOG FILE IMPORTS

// START NET IMPORTS
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import android.content.Context;
// KRAJ NET IMPORTS

// START EXO IMPORTS
import android.net.Uri;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.source.LoadEventInfo;
import com.google.android.exoplayer2.source.MediaLoadData;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
// KRAJ EXO IMPORTS

// IMPORT FOR TIME VARIABLE
import java.time.LocalDateTime;
import java.time.ZoneId;


public class MainActivity extends AppCompatActivity {

    // OBJEDINJUJE SVE PERMISIJE ODJEDNOM

    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private String[] permissions = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
    };

    // NET VARS
    private TextView signalStrengthTextView;

    // EXO VARS - NEW
    private SimpleExoPlayer exoPlayer;
    PlayerView playerView;

    // LISTENERS
    private TextView bitrateTextView;
    private TextView bitrateFromMpdTextView;
    private TextView resolutionTextView;
    private TextView rebufferingTextView;
    private TextView segmentSizeTextView;
    private TextView currentStateTextView;
    private TextView rezolucijaMpdTextView;

    private int initialWidth = -1;
    private int initialHeight = -1;
    private long bufferingStartTime = 0;

    // VARIJABLE EXO BITRATE I OSTALE ZA UPIS U CSV
    private long currentBitrateCsv;
    private long bitrateFromMpdCsv;
    private String resolutionCsv;
    private String rezolucijaMpdCsv;
    private long rebufferingCsv;
    private double segmentSizeCsv;
    private String currentStateCsv;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if permissions are granted
        if (!checkPermissions()) {
            // Request permissions
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS);
        } else {
            // Permissions already granted, start your main code here
            startApp();
        }
    }

    // ODMAH OBJEDINJUJE SVE POTREBNE PERMISIJE OD STRANE USERA
    // TEK ONDA NASTAVLJA SA IZVRSAVANJEM KODA
    private boolean checkPermissions() {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            // Check if all permissions are granted
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                // Permissions granted, start your main code
                startApp();
            } else {
                // Permissions not granted, inform the user or handle accordingly
                // finish();
                // ili
                finishAffinity();
                // ili (ne preporucuje se!)
                // System.exit(0);
            }
        }
    }


    // POZIVA SVAKE SEKUNDE IZBACIVANJE NA DISPLAY I ZAPISIVANJE U CSV NET PARAMETARA
    private void scheduleDisplaySignalStrength() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::displaySignalStrength, 0, 1, TimeUnit.SECONDS);
    }

    // STARTANJE GLAVNOG KODA (NAKON ODOBRENIH PERMISIJA OD STRANE USERA)
    private void startApp() {
        // LOG PART
        // Samo kreira directory i files
        createDirectoryAndFilesIfNeeded();


        // EXO PART
        // Initialize ExoPlayer
        exoPlayer = new SimpleExoPlayer.Builder(this).build();

        // Find the PlayerView from your layout
        playerView = findViewById(R.id.player_view);

        // Set the player to the PlayerView
        playerView.setPlayer(exoPlayer);

        // SET VIEW FOR BITRATE VALUE i OSTALE VALUES
        // Find the TextViews
        bitrateTextView = findViewById(R.id.bitrateTextView);
        bitrateFromMpdTextView = findViewById(R.id.bitrateFromMpdTextView);
        resolutionTextView = findViewById(R.id.resolutionTextView);
        rebufferingTextView = findViewById(R.id.rebufferingTextView);
        segmentSizeTextView = findViewById(R.id.segmentSizeTextView);
        currentStateTextView = findViewById(R.id.currentStateTextView);
        rezolucijaMpdTextView = findViewById(R.id.rezolucijaMpdTextView);

        // Maintain the last known bitrate value
        final long[] lastBitrate = {-1}; // Initialize with an invalid value

        // Set up AnalyticsListener for the player - VAZNO ZA PRIKUPLJANJE NET PARAMETARA BITNO ZA ML!
        exoPlayer.addAnalyticsListener(new AnalyticsListener() {

            //PRVI PARAMETAR - BITRATE
            // OVO JE REALNI (A NE IZ MPD FILE) BITRATE KOJI IMAMO PREMA DATA SOURCE
            @Override
            public void onBandwidthEstimate(@NonNull EventTime eventTime, int totalLoadTimeMs, long totalBytesLoaded, long bitrateEstimate) {
                // This method is called when there is a change in estimated bandwidth/bitrate
                // Check if the new bitrate value is different from the last known value
                if (bitrateEstimate != lastBitrate[0]) {
                    // Update the last known bitrate value
                    lastBitrate[0] = bitrateEstimate;
                    // CSV - DODJELJIVANJE VRIJEDNOSTI
                    currentBitrateCsv = bitrateEstimate / 1_000;
                    // Update the TextView with bitrate information (pretvaramo u Kbps)
                    bitrateTextView.setText("Bitrate (DataSource): " + bitrateEstimate / 1_000 + " Kbps");
                }
            }

            // REZOLUCIJA - ZAVISNO OD DIREKTNE PROMJENE ISTE (REAL TIME DISPLAYED)
            // RADI UPDATE VRIJEDNOSTI SAMO KADA SE REZOLUCIJA PROMIJENI
            @Override
            public void onVideoSizeChanged(@NonNull EventTime eventTime, int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                // Handle video size change event
                if (width != initialWidth || height != initialHeight) {
                    // CSV - DODJELJIVANJE VRIJEDNOSTI
                    resolutionCsv = width + "x" + height;
                    resolutionTextView.setText("Resolution (Displayed): " + width + "x" + height);
                    initialWidth = width;
                    initialHeight = height;
                }
            }

            // REBUFFERING
            @Override
            public void onPlayerStateChanged(@NonNull EventTime eventTime, boolean playWhenReady, int playbackState) {
                // ISPITIVANJE TRENUTNOG STANJA PLAYER-a I RACUNANJE REBUFFERING TIME
                switch (playbackState) {
                    case Player.STATE_BUFFERING:
                        // TRENUTNO STANJE PLAYERA
                        // CSV - DODJELJIVANJE VRIJEDNOSTI
                        currentStateCsv = "STATE_BUFFERING";
                        currentStateTextView.setText("State Current: " + "STATE_BUFFERING");
                        // The player is buffering
                        // Record the start time when buffering begins
                        bufferingStartTime = eventTime.realtimeMs;
                        break;
                    case Player.STATE_READY:
                        // TRENUTNO STANJE PLAYERA
                        // CSV - DODJELJIVANJE VRIJEDNOSTI
                        currentStateCsv = "STATE_READY";
                        currentStateTextView.setText("State Current: " + "STATE_READY");
                        // The player is ready to play
                        // RACUNANJE REBUFFERING TIME
                        if (bufferingStartTime != 0) {
                            long bufferingTime = eventTime.realtimeMs - bufferingStartTime;
                            // Now 'bufferingTime' contains the buffering duration in milliseconds
                            // CSV - DODJELJIVANJE VRIJEDNOSTI
                            rebufferingCsv = bufferingTime;
                            rebufferingTextView.setText("Rebuffering (Last): " + bufferingTime + " ms");
                            bufferingStartTime = 0; // Reset the start time
                        }
                        break;
                    default:

                        // TRENUTNO STANJE PLAYERA
                        // CSV - DODJELJIVANJE VRIJEDNOSTI
                        currentStateCsv = "NEPOZNATO";
                        currentStateTextView.setText("State Current: " + "NEPOZNATO!");

                }
            }

            // RACUNAMO SEGMENT DURATION, BITRATE(MPD), REZOLUCIJA(MPD)
            @Override
            public void onLoadStarted(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo, @NonNull MediaLoadData mediaLoadData) {

                if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA) {
                    // VARIJABLE, POCETAK I KRAJ CHUNK-a (Ms)
                    long chunkStart = mediaLoadData.mediaStartTimeMs;
                    long chunkEnd = mediaLoadData.mediaEndTimeMs;
                    // VARIJABLA ZA DUZINU SEGMENTA (sec)
                    double durationSeconds = (double) (chunkEnd - chunkStart) / 1_000;
                    // VARIJABLA ZA KUPLJENJE BITRATE IZ MPD FILE - NE TRENUTNA IZ MREZE DAKLE !!
                    assert mediaLoadData.trackFormat != null;
                    long bitrateFromMpd = (mediaLoadData.trackFormat.bitrate) / 1_000;
                    // VARIJABLE ZA REZOLUCIJU, ALI IZ MPD FILE
                    long rezolucijaHeightMpd = mediaLoadData.trackFormat.height;
                    long rezolucijaWidthMpd = mediaLoadData.trackFormat.width;

                    // CSV - DODJELJIVANJE VRIJEDNOSTI
                    segmentSizeCsv = durationSeconds;
                    bitrateFromMpdCsv = bitrateFromMpd;
                    rezolucijaMpdCsv = rezolucijaWidthMpd + "x" + rezolucijaHeightMpd;
                    // ISPIS VRIJEDNOSTI NA DISPLAY
                    segmentSizeTextView.setText("Segment Size: " + durationSeconds + " sec");
                    bitrateFromMpdTextView.setText("Bitrate (Mpd): " + bitrateFromMpd + " Kbps");
                    rezolucijaMpdTextView.setText("Resolution (Mpd): " + rezolucijaWidthMpd + "x" + rezolucijaHeightMpd);

                }
            }

        });


        // MEDIA SOURCES boiler plate:

        // Sa lokalnog (Python simple) servera
//        Uri videoUri3 = Uri.parse("http://192.168.1.13:8080/BigBuckBunny/15sec/BigBuckBunny_15s_simple_2014_05_09.mpd");

        // Sa lokalnog (Apache) servera
//        Uri videoUri3 = Uri.parse("http://192.168.1.13:80/BigBuckBunny/15sec/BigBuckBunny_15s_simple_2014_05_09.mpd");

        // Sa public (MISL - ETF) servera
        //Uri videoUri3 = Uri.parse("https://cs1dev.ucc.ie/misl/4K_non_copyright_dataset/2_sec/x264/sintel/DASH_Files/full/sintel_enc_x264_dash.mpd?_gl=1*1g04lou*_gcl_au*MTc0MjkzMTg5LjE3MDUyMjE5MDA.");

        // Sa lokalnog (Apache) servera etf
        Uri videoUri3 = Uri.parse("http://192.168.200.117:80/BigBuckBunny/4sec/BigBuckBunny_4s_simple_2014_05_09.mpd");

        // Sa lokalnog (Apache) servera
        //Uri videoUri3 = Uri.parse("http://192.168.1.222:80/BigBuckBunny/4sec/BigBuckBunny_4s_simple_2014_05_09.mpd");

        // Sa public (Bitmovin) servera
//        Uri videoUri3 = Uri.parse("https://cdn.bitmovin.com/content/assets/art-of-motion-dash-hls-progressive/mpds/f08e80da-bf1d-4e3d-8899-f0f6155f6efa.mpd");


        // UCITAVANJE MEDIA SOURCE boiler plate kod
        MediaItem mediaItem3 = MediaItem.fromUri(videoUri3);
        exoPlayer.setMediaItem(mediaItem3);

        // DataSource Factory MPD files
        // Create a DefaultHttpDataSource Factory
        DefaultHttpDataSource.Factory dataSourceFactory = new DefaultHttpDataSource.Factory();
        // Create a DashMediaSource.Factory
        DashMediaSource.Factory dashMediaSourceFactory = new DashMediaSource.Factory(dataSourceFactory);
        // MPD
        MediaSource mediaSource3 = dashMediaSourceFactory.createMediaSource(mediaItem3);

        // SETUJE MEDIA SOURCE
        exoPlayer.setMediaSource(mediaSource3);

        // Start playback when ready
        exoPlayer.setPlayWhenReady(true);
        // Prepare the player
        exoPlayer.prepare();
        // Play
        exoPlayer.play();


        // POZIVA SCHEDULER - KOJI OPET POZIVA METODE U REGULARNIM, DEFINISANIM INTERVALIMA
        scheduleDisplaySignalStrength();

    }


    // LOG PART
    private void createDirectoryAndFilesIfNeeded() {


        @SuppressLint("HardwareIds") String ANDRO_ID = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
//OLD VERSION BEFORE IMEI
//        String directoryName = "logExoNet777";
//        String exoFileName = "logExo.csv";
//        String netFileName = "logNet.csv";

        String directoryName = "logExoNet777_" + ANDRO_ID;
        String exoFileName = "logExo_" + ANDRO_ID + ".csv";
        String netFileName = "logNet_" + ANDRO_ID + ".csv";
        // UPDATE ZA TRECI FILE
        String exoANDnetFileName = "logExoANDNet_" + ANDRO_ID + ".csv";


        File directory = new File(getExternalFilesDir(null), directoryName);
        if (!directory.exists()) {
            if (directory.mkdirs()) {
                createFile(directoryName, exoFileName, "Bitrate (DataSource),Bitrate (Mpd),Resolution (Displayed),Resolution (Mpd),Rebuffering (Last),Segment Size,State Current\n");
                createFile(directoryName, netFileName, "CurrentDateTime,RSSI,RSRP,RSRQ,RSSNR,CQI,TA,Level,ANDRO_ID\n"); // ubacen ANDRO_ID na kraj, i currentDateTime na pocetak

                // UPDATE - FORMIRAMO I TRECI MERGOVANI FILE OD GORNJA DVA
                createFile(directoryName, exoANDnetFileName, "CurrentDateTime,RSSI,RSRP,RSRQ,RSSNR,CQI,TA,Level,ANDRO_ID,Bitrate (DataSource),Bitrate (Mpd),Resolution (Displayed),Resolution (Mpd),Rebuffering (Last),Segment Size,State Current\n");
            }
        }
    }

    private void createFile(String directoryPath, String fileName, String header) {

        File directory = new File(getExternalFilesDir(null), directoryPath);

        File filePath = new File(directory, fileName);

        try {
            FileWriter csvWriter = new FileWriter(filePath, true);
            csvWriter.append(header);
            csvWriter.flush();
            csvWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void writeParameterToCSV(String directoryPath, String fileName, String parameters) {
        File directory = new File(getExternalFilesDir(null), directoryPath);
        File filePath = new File(directory, fileName);

        try {
            FileWriter csvWriter = new FileWriter(filePath, true);
            csvWriter.append(parameters).append("\n");
            csvWriter.flush();
            csvWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // VAZNA !!!
    private void displaySignalStrength() {

        // UPDATE - UBACUJEMO CURRENT TIME VARIJABLU
        // Get the current time in milliseconds
        long currentTimeMillis = System.currentTimeMillis();
        // Convert milliseconds to LocalDateTime
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(currentTimeMillis),
                ZoneId.systemDefault()
        );
        // Display the current time in the logcat
        Log.d("CurrentTime_HABER", "Current time: " + currentDateTime);
        // KRAJ ZA TIME VARIJABLU


        signalStrengthTextView = findViewById(R.id.signal_strength_text);

        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }


            // UPDATE - IMEI PARAMETER
            // TRAZI SPECIJALNE PERMISIJE !!!!!!!!
            // String IMEI_07 = telephonyManager.getImei();
            // 2. POKUSAJ - ISTO TRAZI SPECIJALNE PERMISIJE !!!!!!!!
            // String IMEI_07 = telephonyManager.getDeviceId();
            // 3. POKUSAJ (samo testno, ovu varijablu, treba zamijeniti sa pravim IMEI)
            // String IMEI_07 = telephonyManager.getDeviceSoftwareVersion();
            // PREKO ANDROID ID
            // STARA POZICIJA, PREBACENA IZNAD, ali mora ostati i ovdje
            @SuppressLint("HardwareIds") String ANDRO_ID = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);


            // PROVJERA PREKO LOGCAT
            // Log.d("IMEI", "IMEI VALUEZZZ: " + ANDRO_ID);


            String listaDf2 = telephonyManager.getSignalStrength().getCellSignalStrengths().toString();

            // POREDAK PARAMETARA = "CellSignalStrengthLte: rssi=-89 rsrp=-89 rsrq=-10 rssnr=100 cqi=12 ta=2147483647 level=3";

            // ukljanja zadnju zagradu at the end to ensure the last parameter ends with a space for consistency
            listaDf2 = listaDf2.substring(0, listaDf2.length() - 1);

            String[] parameters = listaDf2.split("\\s+");

            StringBuilder netParams = new StringBuilder();

            // Kreiramo array list, za print
            ArrayList<String> netParams2Prn = new ArrayList<String>();

            for (String parameter : parameters) {
                String[] parts = parameter.split("=");

                if (parts.length == 2) {
                    String paramName = parts[0];
                    String paramValue = parts[1];

                    netParams
                            .append(paramValue)
                            .append(",");

                    netParams2Prn.add(paramValue);

                }
            }

            // netParams.deleteCharAt(netParams.length() - 1); // brise zadnji zarez - UZ UPDATE IMEI - TEMP UGASENO
            // NAPOMENA - LINIJU IZNAD UPALITI KADA SE IZBACI KOD ZA IMEI POTREBNO ZA MASTER RAD

            // UPDATE - DODAJEMO IMEI PARAMETER
            // ZA PRINT NA EKRAN
            netParams2Prn.add(ANDRO_ID);
            // ZA ZAPIS U CSV
            netParams.append(ANDRO_ID);
            // ubacujemo time ALI na pocetak zapisa
            netParams.insert(0, currentDateTime + ",");


            // PRINTA GRUPNO VALUES
            // rssi=-89 rsrp=-89 rsrq=-10 rssnr=100 cqi=12 ta=2147483647 level=3" - POREDAK
            String displayText = "Network Parameters:" + "\n" +
                    "RSSI: " + netParams2Prn.get(0) + " dBm\n" +
                    "RSRP: " + netParams2Prn.get(1) + " dBm\n" +
                    "RSRQ: " + netParams2Prn.get(2) + " dB\n" +
                    "RSSNR: " + netParams2Prn.get(3) + " dB\n" +
                    "CQI: " + netParams2Prn.get(4) + "\n" +
//                    "TA: " + netParams2Prn.get(5) + "JDNC\n" + // OVAJ PARAMETAR UKLONITI !!
                    "Level: " + netParams2Prn.get(6) + "\n" +
                    // UPDATE ZA IMEI PARAMETER
                    "ANDRO_ID: " + netParams2Prn.get(7);


            signalStrengthTextView.setText(displayText);

            // NOVI DIO ZA ZAPIS U CSV
            String directoryName = "logExoNet777_" + ANDRO_ID;
            String exoFileName = "logExo_" + ANDRO_ID + ".csv";
            String netFileName = "logNet_" + ANDRO_ID + ".csv";
            // UPDATE ZA TRECI MERGED FILE
            String exoANDnetFileName = "logExoANDNet_" + ANDRO_ID + ".csv";

            File directory = new File(getExternalFilesDir(null), directoryName);

            StringBuilder exoParams = new StringBuilder();

            // EXO PARAMS
            exoParams
                    .append(currentBitrateCsv)
                    .append(",")
                    .append(bitrateFromMpdCsv)
                    .append(",")
                    .append(resolutionCsv)
                    .append(",")
                    .append(rezolucijaMpdCsv)
                    .append(",")
                    .append(rebufferingCsv)
                    .append(",")
                    .append(segmentSizeCsv)
                    .append(",")
                    .append(currentStateCsv);


            writeParameterToCSV(directoryName, exoFileName, exoParams.toString());
            writeParameterToCSV(directoryName, netFileName, netParams.toString());
            // UPDATE ZA TRECI MERGED FILE
            String exoANDnetParams = netParams.toString() + "," + exoParams.toString();
            writeParameterToCSV(directoryName, exoANDnetFileName, exoANDnetParams);


        }
    }


    // EXO PART
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (exoPlayer != null) {
            exoPlayer.release();
        }


    }

}




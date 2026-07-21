package com.example.busoffline;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Fully offline bus-line lookup.
 * Reads assets/stations.json once at startup (bundled inside the app - no internet,
 * no server, nothing external at runtime) and lets the user pick an origin and a
 * destination station from two dropdowns, then lists every line that connects them
 * in that direction, with its departure times.
 *
 * To update the data: replace assets/stations.json with a new file built from the
 * Ministry of Transport GTFS feed (see gtfs_to_json.py) and rebuild the APK - or,
 * if you want update-without-rebuild, change loadData() below to instead read the
 * file from external/SD storage so a new json can just be copied onto the device.
 */
public class MainActivity extends Activity {

    private List<String> stationNames = new ArrayList<>();
    private List<BusLine> lines = new ArrayList<>();

    private Spinner fromSpinner;
    private Spinner toSpinner;
    private ListView resultsList;

    private static class BusLine {
        String lineNumber;
        String operator;
        List<String> stops = new ArrayList<>();
        List<String> times = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fromSpinner = findViewById(R.id.fromSpinner);
        toSpinner = findViewById(R.id.toSpinner);
        resultsList = findViewById(R.id.resultsList);
        Button searchButton = findViewById(R.id.searchButton);

        loadData();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, stationNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromSpinner.setAdapter(adapter);
        toSpinner.setAdapter(adapter);

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String from = (String) fromSpinner.getSelectedItem();
                String to = (String) toSpinner.getSelectedItem();
                if (from == null || to == null || from.equals(to)) {
                    Toast.makeText(MainActivity.this, "בחר מוצא ויעד שונים", Toast.LENGTH_SHORT).show();
                    return;
                }
                runSearch(from, to);
            }
        });
    }

    /** Reads and parses assets/stations.json into memory. */
    private void loadData() {
        try {
            InputStream is = getAssets().open("stations.json");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject root = new JSONObject(sb.toString());

            JSONArray stationsArr = root.getJSONArray("stations");
            for (int i = 0; i < stationsArr.length(); i++) {
                stationNames.add(stationsArr.getString(i));
            }

            JSONArray linesArr = root.getJSONArray("lines");
            for (int i = 0; i < linesArr.length(); i++) {
                JSONObject lineObj = linesArr.getJSONObject(i);
                BusLine bl = new BusLine();
                bl.lineNumber = lineObj.getString("line");
                bl.operator = lineObj.optString("operator", "");

                JSONArray stopsArr = lineObj.getJSONArray("stops");
                for (int s = 0; s < stopsArr.length(); s++) {
                    bl.stops.add(stopsArr.getString(s));
                }

                JSONArray timesArr = lineObj.getJSONArray("times");
                for (int t = 0; t < timesArr.length(); t++) {
                    bl.times.add(timesArr.getString(t));
                }

                lines.add(bl);
            }
        } catch (Exception e) {
            Toast.makeText(this, "שגיאה בטעינת נתוני התחנות: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Finds every line where 'from' appears before 'to' in the stop order, and displays it. */
    private void runSearch(String from, String to) {
        List<String> resultLines = new ArrayList<>();

        for (BusLine bl : lines) {
            int fromIndex = bl.stops.indexOf(from);
            int toIndex = bl.stops.indexOf(to);
            if (fromIndex != -1 && toIndex != -1 && fromIndex < toIndex) {
                StringBuilder row = new StringBuilder();
                row.append("קו ").append(bl.lineNumber);
                if (!bl.operator.isEmpty()) row.append(" (").append(bl.operator).append(")");
                row.append(" - יציאות: ").append(String.join(", ", bl.times));
                resultLines.add(row.toString());
            }
        }

        if (resultLines.isEmpty()) {
            resultLines.add(getString(R.string.no_results));
        }

        ArrayAdapter<String> resultsAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, resultLines);
        resultsList.setAdapter(resultsAdapter);
    }
}

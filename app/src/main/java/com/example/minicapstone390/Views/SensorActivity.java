package com.example.minicapstone390.Views;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationCompat;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.minicapstone390.Controllers.Database;
import com.example.minicapstone390.Controllers.SharedPreferenceHelper;
import com.example.minicapstone390.Models.Device;
import com.example.minicapstone390.Models.Sensor;
import com.example.minicapstone390.Models.SensorData;
import com.example.minicapstone390.R;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

// DateTime
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;

import java.util.List;
import java.util.Objects;

public class SensorActivity extends AppCompatActivity {
    private static final String TAG = "SensorActivity";

    // Declare variables
    private final Database dB = new Database();
    protected SharedPreferenceHelper sharePreferenceHelper;
    protected LineChart sensorChart;
    protected TextView chartTitle;
    protected RadioGroup graphTimesOptions;
    protected Toolbar toolbar;
    protected String sensorId;
    protected String function;

    public int graphTimeScale = 7;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialize SharedPref and check theme
        sharePreferenceHelper = new SharedPreferenceHelper(SensorActivity.this);
        // Set theme
        if (sharePreferenceHelper.getTheme()) {
            setTheme(R.style.NightMode);
        } else {
            setTheme(R.style.LightMode);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sensor);

        // Enable toolbar
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        graphTimesOptions = (RadioGroup) findViewById(R.id.graphTimeOptions);
        graphTimesOptions.check(R.id.weekButton);

        chartTitle = (TextView) findViewById(R.id.chart_title);
        sensorChart = (LineChart) findViewById(R.id.sensorChart);
        // Disable legend and description
        sensorChart.getLegend().setEnabled(false);
        sensorChart.getDescription().setEnabled(false);

        Bundle carryOver = getIntent().getExtras();
        if (carryOver != null) {
            sensorId = carryOver.getString("sensorId");
            function = carryOver.getString("callFunction", "");

            if(function.equals("editSensor()")) {
                editSensor(sensorId);
            } else if (function.equals("deleteSensor()")) {
                deleteSensorData(sensorId);
            }
            if (sensorId != null) {
                displaySensorInfo(sensorId);
                getAllSensorData();
            } else {
                Log.e(TAG, "Id is null");
                openHomeActivity();
            }
        } else {
            Toast.makeText(this, "Error fetching device", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error fetching device");
            openHomeActivity();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onResume() {
        super.onResume();
        displaySensorInfo(sensorId);
        setGraphScale();
    }

    private void deleteSensorData(String sensorId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle("Delete Sensor Data Confirmation");
        builder.setMessage("Deleting will completely remove the Sensors stored data");
        builder.setPositiveButton("Confirm",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        if (dB.getSensorChild(sensorId) != null) {
                            dB.getSensorChild(sensorId).addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    if (snapshot.child("SensorPastValues").exists()) {
                                        snapshot.child("SensorPastValues").getRef().removeValue().addOnCompleteListener(new OnCompleteListener<Void>() {
                                            @Override
                                            public void onComplete(@NonNull Task<Void> task) {
                                                if (!task.isSuccessful()) {
                                                    Log.d(TAG, String.format("Unable to delete sensor data: %s", sensorId));
                                                } else {
                                                    Log.i(TAG, String.format("Removed sensor data: %s", sensorId));
                                                    onSupportNavigateUp();
                                                }
                                            }
                                        });
                                    } else {
                                        Log.e(TAG, "Error retrieving SensorPastValues from DB");
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError e) {
                                    Log.d(TAG, e.toString());
                                    throw e.toException();
                                }
                            });
                        }
                    }
                });
        builder.setNegativeButton("Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Log.i(TAG, "Sensor data delete cancelled");
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void editSensor(String sensorId) {
        Bundle bundle = new Bundle();
        bundle.putString("id", sensorId);
        SensorFragment dialog = new SensorFragment();
        dialog.setArguments(bundle);
        dialog.show(getSupportFragmentManager(), "SensorFragment");
    }

    // Display basic info of the sensor
    private void displaySensorInfo(String sensorId) {
        DatabaseReference sensorRef = dB.getSensorChild(sensorId);

        sensorRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chartTitle.setText(getResources().getString(R.string.sensor_graph).replace("{0}", Objects.requireNonNull(snapshot.child("SensorName").getValue(String.class))));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                Log.d(TAG, e.toString());
                throw e.toException();
            }
        });
    }

    // Set the graph scale when button is selected
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setGraphScale() {
        graphTimesOptions.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int id) {
                switch (id) {
                    case R.id.dayButton:
                        graphTimeScale = 0;
                        break;
                    case R.id.weeksButton:
                        graphTimeScale = 14;
                        break;
                    case R.id.monthButton:
                        graphTimeScale = 28;
                        break;
                    default:
                        graphTimeScale = 7;
                }
                getAllSensorData();
            }
        });
    }

    // TODO: Fix spaghetti
    // Get the time scale of the X axis of the graph
    @RequiresApi(api = Build.VERSION_CODES.O)
    private ArrayList<LocalDateTime> updateGraphDates() {
        List<LocalDateTime> history = new ArrayList<>();
        setGraphScale();
        long decrement = graphTimeScale / 7;
        if (decrement == 0) {
            for (long i = 23; i >= 0; i -= 4) {
                history.add(LocalDateTime.now().minusHours(i));
            }
            history.add(LocalDateTime.now());
        } else {
            for (long i = graphTimeScale; i >= 0; i -= decrement) {
                history.add(LocalDateTime.now().minusDays(i));
            }
        }
        return new ArrayList<>(history);
    }

    public void getAllSensorData() {
        ArrayList<SensorData> validData = new ArrayList<>();
        dB.getSensorChild(sensorId).child("SensorPastValues").addValueEventListener(new ValueEventListener() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                ArrayList<Double> values = new ArrayList<>();
                ArrayList<LocalDateTime> times = new ArrayList<>();
                ArrayList<LocalDateTime> history = updateGraphDates();

                LocalDateTime start = history.get(0);
                LocalDateTime end = history.get(history.size() - 1);

                for (DataSnapshot ds : snapshot.getChildren()) {
                    if (ds.exists()) {
                        LocalDateTime time = LocalDateTime.parse(ds.getKey(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        if (start.isBefore(time) && end.isAfter(time)) {
                            if (ds.child("Value").exists()) {
                                values.add(ds.child("Value").getValue(Double.class));
                                times.add(LocalDateTime.parse(ds.getKey(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                            } else {
                                Log.e(TAG, "Error retrieving PastValues Value from DB");
                            }
                        }
                    } else {
                        Log.e(TAG, "Error retrieving SensorPastValues from DB");
                    }
                }
                validData.add(new SensorData(values, times));
                producer(history, validData.get(0));
                validData.clear();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                Log.d(TAG, e.toString());
                throw e.toException();
            }
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void producer(List<LocalDateTime> history, SensorData data) {
        if (data.getValues().size() != 0) {
            LocalDateTime start = history.get(0);
            LocalDateTime end = history.get(history.size() - 1);
            long duration = Duration.between(start, end).getSeconds();
            long cuts = data.getValues().size();
            long delta = duration / (cuts - 1);
            ArrayList<LocalDateTime> results = new ArrayList<>();

            for (int i = 0; i < cuts; i++) {
                results.add(start.plusSeconds(i * delta));
            }
            setXAxisLabels(history, data, results);
        }
    }

    // Setting LineChart
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setXAxisLabels(List<LocalDateTime> history, SensorData data, ArrayList<LocalDateTime> results) {
        ArrayList<String> xAxisLabel = new ArrayList<>(results.size());
        DateTimeFormatter format;
        if (graphTimesOptions.getCheckedRadioButtonId() == R.id.dayButton) {
            format = DateTimeFormatter.ofPattern("HH:mm");
        } else {
            format = DateTimeFormatter.ofPattern("MM/dd");
        }

        for (int i = 0; i < results.size(); i++) {
            xAxisLabel.add(results.get(i).format(format));
        }

        XAxis xAxis = sensorChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setLabelCount(history.size() + 1,true);
        xAxis.setCenterAxisLabels(true);
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override
            public String getFormattedValue(float value, AxisBase axis) {
                if (value < results.size()) {
                    return xAxisLabel.get((int) value);
                }
                return "";
            }
        });
        setYAxis();
        setData(data, results);
    }

    private void setYAxis() {
        YAxis leftAxis = sensorChart.getAxisLeft();
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        leftAxis.setTextColor(Color.BLACK);
        leftAxis.setGranularityEnabled(true);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setAxisMaximum(1.1f);
        leftAxis.setGranularity(0.1f);

        YAxis rightAxis = sensorChart.getAxisRight();
        rightAxis.setEnabled(false);
    }

    // TODO: Fix spaghetti
    @RequiresApi(api = Build.VERSION_CODES.O)
    protected void setData(SensorData data, ArrayList<LocalDateTime> results) {
        ArrayList<Entry> values = new ArrayList<>();
        for (int x = 1; x < results.size() - 1; x++) {
            LocalDateTime start = results.get(0);
            LocalDateTime end = results.get(results.size() - 1);

            // TODO: Check if first state is ever passing? Appending -1 to start isn't working
            if (data.getTimes().get(x).isBefore(start) || data.getTimes().get(x).isAfter(end)) {
                values.add(new Entry(x, -1));
            } else {
                values.add(new Entry(x, data.getValues().get(x).floatValue()));
            }
        }

        LineDataSet set = new LineDataSet(values, "SensorGraph");
        set.setDrawValues(false);
        set.setLineWidth(2);

        LineData lineData = new LineData(set);
        lineData.setValueTextColor(Color.BLACK);
        lineData.setValueTextSize(9f);

        sensorChart.setData(lineData);
        sensorChart.invalidate();
    }

    private void notification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "id").setContentTitle("Notif").setContentText("Over 10").setPriority(NotificationCompat.PRIORITY_DEFAULT);
    }

    // Display options menu in task-bar
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sensor_menu, menu);
        return true;
    }

    // Create the action when an option on the task-bar is selected
    @Override
    public  boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.disable_sensor) {
            Log.d(TAG, "Disable sensor called but not implemented");
            disableSensor();
        } else if (id == R.id.delete_sensor_data) {
            deleteSensorData(sensorId);
        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    // TODO: Add status to DB
    private void disableSensor() {
        dB.getSensorChild(sensorId).child("status").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                try {
                    if (snapshot.exists()) {
                        if (snapshot.getValue(Boolean.class)) {
                            snapshot.getRef().setValue(false);
                        } else {
                            snapshot.getRef().setValue(true);
                        }
                    } else {
                        Log.e(TAG, "Error retrieving sensor status from DB");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d(TAG, e.toString());
                    throw e;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError e) {
                Log.d(TAG, e.toString());
                throw e.toException();
            }
        });
    }

    // Navigate back to Home Activity
    private void openHomeActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
    }

    // Navigate back to device page on task-bar return
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

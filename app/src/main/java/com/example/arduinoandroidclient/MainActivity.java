package com.example.arduinoandroidclient;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    EditText ipEdit, portEdit, bitValueEdit;
    TextView tempResult, humResult;
    Socket socket;
    PrintWriter out;
    BufferedReader in;
    LineChart lineChart;
    ArrayList<Entry> tempEntries = new ArrayList<>();
    ArrayList<Entry> humEntries = new ArrayList<>();
    int timeIndex = 0; // X축 시간 흐름 표현용 인덱스
    private boolean isAutoReading = false;
    private android.os.Handler autoHandler = new android.os.Handler();
    void startAutoRead() {
        isAutoReading = true;
        autoHandler.post(autoReadRunnable);
    }

    void stopAutoRead() {
        isAutoReading = false;
        autoHandler.removeCallbacks(autoReadRunnable);
    }

    Runnable autoReadRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAutoReading) {
                requestTemp();
                autoHandler.postDelayed(this, 1000); // 1초 주기
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipEdit = findViewById(R.id.ipEdit);
        bitValueEdit = findViewById(R.id.bitValueEdit);
        tempResult = findViewById(R.id.tempResult);
        humResult = findViewById(R.id.humResult);
        portEdit = findViewById(R.id.portEdit);

        findViewById(R.id.connectButton).setOnClickListener(v -> connect());
        findViewById(R.id.sendButton).setOnClickListener(v -> sendLed());
        findViewById(R.id.resetButton).setOnClickListener(v -> clearFields());
        findViewById(R.id.exitButton).setOnClickListener(v -> closeApp());
        lineChart = findViewById(R.id.lineChart);
        lineChart.getDescription().setText("실시간 온습도 그래프");
        lineChart.getLegend().setEnabled(true);
        findViewById(R.id.autoStartButton).setOnClickListener(v -> startAutoRead());
        findViewById(R.id.autoStopButton).setOnClickListener(v -> stopAutoRead());
        findViewById(R.id.manualReadButton).setOnClickListener(v -> requestTemp());
    }

    void connect() {
        new Thread(() -> {
            try {
                String ip = ipEdit.getText().toString().trim();
                int port = Integer.parseInt(portEdit.getText().toString().trim());

                socket = new Socket(ip, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                runOnUiThread(() ->
                        Toast.makeText(this, "서버 연결 성공", Toast.LENGTH_SHORT).show());

                // ❌ startAutoUpdate(); ← 이 줄 삭제
                // ✅ 대신 수동으로 startAutoRead() 하도록 유저에게 맡기기
            } catch (NumberFormatException e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "올바른 포트 번호를 입력하세요.", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "연결 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    void sendLed() {
        String value = bitValueEdit.getText().toString();
        sendCommand("LED=" + value);
    }

    void requestTemp() {
        new Thread(() -> {
            sendCommand("GET_TEMP");
            try {
                String response = in.readLine();
                runOnUiThread(() -> {
                    if (response != null && response.startsWith("TEMP=")) {
                        String[] parts = response.replace("TEMP=", "").replace("HUM=", "").split(",");
                        float temp = Float.parseFloat(parts[0]);
                        float hum = Float.parseFloat(parts[1]);

                        tempResult.setText("온도 값: " + temp + "℃");
                        humResult.setText("습도 값: " + hum + "%");
                        updateChart(temp, hum); // ✅ 그래프 갱신 추가
                    } else {
                        tempResult.setText("응답 오류");
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> tempResult.setText("수신 실패"));
            }
        }).start();
    }

    void sendCommand(String cmd) {
        if (socket == null || socket.isClosed()) return;
        new Thread(() -> {
            try {
                if (out != null) {
                    out.println(cmd);
                    in.readLine(); // 응답 소비
                }
            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "명령 전송 실패", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    void clearFields() {
        bitValueEdit.setText("");
        tempResult.setText("온도 값: -");
        humResult.setText("습도 값: -");
    }

    void closeApp() {
        try {
            stopAutoRead(); // ✅ 자동 읽기 먼저 중단

            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        finish(); // 액티비티 종료
    }
    void updateChart(float temp, float hum) {
        tempEntries.add(new Entry(timeIndex, temp));
        humEntries.add(new Entry(timeIndex, hum));
        timeIndex++;

        if (tempEntries.size() > 20) {
            tempEntries.remove(0);
            humEntries.remove(0);
        }

        LineDataSet tempDataSet = new LineDataSet(tempEntries, "온도(℃)");
        tempDataSet.setColor(Color.RED);
        tempDataSet.setValueTextColor(Color.RED);
        tempDataSet.setLineWidth(2f);
        tempDataSet.setCircleRadius(3f);

        LineDataSet humDataSet = new LineDataSet(humEntries, "습도(%)");
        humDataSet.setColor(Color.BLUE);
        humDataSet.setValueTextColor(Color.BLUE);
        humDataSet.setLineWidth(2f);
        humDataSet.setCircleRadius(3f);

        LineData lineData = new LineData(tempDataSet, humDataSet);
        lineChart.setData(lineData);
        lineChart.invalidate(); // 그래프 갱신
    }
}

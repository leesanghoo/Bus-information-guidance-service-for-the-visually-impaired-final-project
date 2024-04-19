package com.example.finalproject;

import static android.speech.tts.TextToSpeech.ERROR;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * 2022-03-29 TTS (TextToSpeech) 확인
 * 2022-03-29 getXmlData_BusInfo 확인 필요
 * 2022-03-29 getXmlData_Busnum 확인 필요
 * 2022-03-29 Intent 화면전환 확인
 * 2022-03-30 길찾기 기능 수정
 * 2022-03-30 통신코드 수정 및 확인 필요
 *  1. MyApplication 들어가서 AppCompatActivity확인
 *  2. 알고리즘대로 코드 수정
 *  2022-04-02 도시코드 추후에 추가 예정, 현재 청주시 버스정보 앱
 *  2022-04-02 구글맵길찾기에서 얻은 버스정류장과 동일한 이름의 버스정류장이 여러개 존재하는 문제가 있어 경도, 위도 값으로 버스정류장 찾기
 *  2022-04-02 구글맵 API 버스정류장 경도 위도 값과 국토교통부에서 제공하는 버스정류장 경도 위도 값이 다른 오류 발생
 **/

public class MainActivity extends AppCompatActivity {


    private TextToSpeech tts;
    private Button btn_search;
    private Button btn_toBusInfo;
    private EditText et_dest;
    private EditText et_origin;
    String busnum;
    String busstop;
    String busstop_lng;
    String busstop_lat;

    //길찾기 기능 관련
    private static final String Google_API_KEY = "비공개KEY";
    private String str_url = null; // EditText의 값과 원래의 URL을 합쳐 검색 URL을 만들어 저장
    private String step = null; // 한 루트에 여러 개의 단계가 존재하므로 한 단계를 저장하기 위한 변수
    private String entire_step = null; // 여러 개의 단계를 하나의 단계로 합쳐 저장하기 위한 변수
    private int list_len = 0; // 배열의 동적 생성을 위한 변수
    private TextView tv_wayInfo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //타이틀바 지우기
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        btn_search = findViewById(R.id.btn_search);
        btn_toBusInfo = findViewById(R.id.btn_toBusInfo);
        et_origin = findViewById(R.id.et_origin);
        et_dest = findViewById(R.id.et_dest);
        tv_wayInfo=findViewById(R.id.tv_wayInfo);


        //TTS(TextToSpeech) 초기 설정
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                    tts.speak("출발지와 목적지를 입력한 후," +
                            "상단의 버튼을 누르면 길찾기 기능을 수행합니다. " +
                            "하단의 버튼을 누르면 버스 안내 기능을 수행합니다.",TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        });

        //EditText 클릭하면 음성출력
        et_origin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tts.speak("출발지 입력",TextToSpeech.QUEUE_FLUSH, null);
            }
        });


        //EditText 클릭하면 음성출력
        et_dest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tts.speak("목적지 입력",TextToSpeech.QUEUE_FLUSH, null);
            }
        });

        //검색 클릭하면 길찾기 기능 수행
        btn_search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //목적지-> 길찾기-가까운 버스정류장 찾기
                find_way(view);
            }
        });


        //다음 Activity로 화면전환
        btn_toBusInfo.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {


                //버스정류장, 버스 번호 값넘기기


                //str 수정필요


                //busstop=et_dest.getText().toString();   //string으로 바꿔서 et에 입력한 text 갖고오기
                Intent intent = new Intent(MainActivity.this, SubActivity.class);
                intent.putExtra("busstop_lat", busstop_lat);
                intent.putExtra("busstop_lng", busstop_lng);
                intent.putExtra("busstop", busstop);
                intent.putExtra("busnum", busnum);
                startActivity(intent); //액티비티 이동
            }
        });




    }



    //길찾기 함수
    void find_way(View view){
        String depart = et_origin.getText().toString();
        String arrival = et_dest.getText().toString();
        str_url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + depart +
                "&destination=" + arrival +
                "&mode=transit&departure_time=now&alternatives=true&language=ko&key=" + Google_API_KEY;

        String resultText = "값이 없음";

        try {
            resultText = new Task().execute().get(); // URL 연결하는 함수를 호출한 후 반환

            JSONObject jsonObject = new JSONObject(resultText);
            String routes = jsonObject.getString("routes");
            JSONArray routesArray = new JSONArray(routes);
            JSONObject subJsonObject = routesArray.getJSONObject(0);

            String legs = subJsonObject.getString("legs");
            JSONArray LegArray = new JSONArray(legs);
            JSONObject legJsonObject = LegArray.getJSONObject(0);

            String steps = legJsonObject.getString("steps");
            JSONArray stepsArray = new JSONArray(steps);
            list_len = stepsArray.length(); // stepsArray의 길이를 list_len 변수에 저장

            String[] getInstructions = new String[list_len];
            // Array 길이를 가지는 동적 배열을 생성
            String[] arrival_name = new String[list_len];
            String[] depart_name = new String[list_len];
            String[] getHeadsign = new String[list_len];
            String[] getBusNo = new String[list_len];
            String[] depart_lat= new String[list_len];
            String[] depart_lng= new String[list_len];


            for (int i = 0; i < list_len; i++) { // 리스트의 길이만큼 반복

                    JSONObject stepsObject = stepsArray.getJSONObject(i);
                getInstructions[i] = stepsObject.getString("html_instructions");

                // stepsArray에서 키값이 html_instructions인 value를 배열에 저장
                String[] Check = getInstructions[i].split(" ");
                // 현재의 단계가 버스나 지하철 같은 대중교통을 이용하는지 확인하기 위해
                // html_instructions의 value를 split 함수를 이용해 공백을 기준으로 잘라 배열에 저장
                String TransitCheck = Check[0];
                // html_instructions의 value에서 제일 처음 공백 전에 버스인지 지하철인지 나오므로
                // 배열의 0번째 요소를 TransitCheck 변수에 저장
                if (TransitCheck.equals("Bus") || TransitCheck.equals("버스")) {
                    String transit_details = stepsObject.getString("transit_details");
                    JSONObject transitObject = new JSONObject(transit_details);

                    String arrival_stop = transitObject.getString("arrival_stop");
                    JSONObject arrivalObject = new JSONObject(arrival_stop);
                    arrival_name[i] = arrivalObject.getString("name");

                    String depart_stop = transitObject.getString("departure_stop");
                    JSONObject departObject = new JSONObject(depart_stop);

                    String depart_loc = departObject.getString("location");
                    JSONObject departLocObject = new JSONObject(depart_loc);
                    depart_lat[i]= departLocObject.getString("lat");
                    depart_lng[i]= departLocObject.getString("lng");

                    depart_name[i] = departObject.getString("name");



                    getHeadsign[i] = transitObject.getString("headsign");

                    String line = transitObject.getString("line");
                    JSONObject lineObject = new JSONObject(line);
                    getBusNo[i] = lineObject.getString("short_name");
                }


                String[] busInfoArr={"", "", "", ""};
                if(depart_name[i]==null || arrival_name[i]==null){
                    step = getInstructions[i] + "\n";
                } else if(depart_name[i]!=null || arrival_name[i]!=null){
                    step = //depart_lat[i]+", "+depart_lng[i] +"\n"+
                            depart_name[i] + " 버스정류장에서 승차." + "\n" +
                            getHeadsign[i] + " 방향, " + getBusNo[i] + "번 버스 탑승. "+ "\n" +
                            arrival_name[i] + " 에서 하차. " + "\n ";


                    busstop_lat = depart_lat[i];
                    busstop_lng = depart_lng[i];
                    busstop=depart_name[i];
                    busnum=getBusNo[i];

                    //busInfoArr= new String[]{busstop_lat, busstop_lng, busstop, busnum};

                }


                if (entire_step == null) {
                    entire_step = step;
                } else { // 각 단계를 하나의 단계로 만들기 위해 entire_step에 더해준다.
                    entire_step += step;
                }
            }

            tv_wayInfo.setText(entire_step);
            tts.speak(entire_step,TextToSpeech.QUEUE_FLUSH, null);


        } catch (InterruptedException e) { // 예외 처리
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }



    //Directions API 호출
    public class Task extends AsyncTask<String, Void, String> {
        private String str, receiveMsg;

        @Override protected String doInBackground(String... params) {
            URL url = null;
            try {
                url = new URL(str_url); // str_url로 연결
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                //url.openConnection()을 이용해서, HTTP Connection을 열고 호출

                if (conn.getResponseCode() == conn.HTTP_OK) {
                    InputStreamReader tmp = new InputStreamReader(conn.getInputStream(), "UTF-8");
                    BufferedReader reader = new BufferedReader(tmp);
                    StringBuffer buffer = new StringBuffer();
                    while ((str = reader.readLine()) != null) {
                        buffer.append(str);
                    }
                    receiveMsg = buffer.toString(); // JSON 파일을 String으로 바꿔 receiveMsg 변수에 저장

                    reader.close();
                } else {
                    Log.i("통신 결과", conn.getResponseCode() + "에러");
                }
            } catch (MalformedURLException e) {
                // 예외 처리
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } return receiveMsg;
        }

    }
}
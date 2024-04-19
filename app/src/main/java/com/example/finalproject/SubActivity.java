package com.example.finalproject;

import static android.speech.tts.TextToSpeech.ERROR;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public class SubActivity extends AppCompatActivity {

    TextView text1, text2, text3;
    TextView tv_test;
    XmlPullParser xpp;
    String busstop_ID;
    String bus_arrInfo="버스도착정보가 없음";
    String route;
    ArrayList<String> BusesPlateNum_arr=new ArrayList<String>();
    ArrayList<String> BusStops_arr1=new ArrayList<String>();
    ArrayList<String> BusStops_arr2=new ArrayList<String>();

    private Button btn;
    private TextToSpeech tts;

    //통신코드
    private Socket client;
    private DataOutputStream dataOutput;
    private DataInputStream dataInput;
    private static String CONNECT_MSG = "connect";
    private static String STOP_MSG = "stop";
    private static int BUF_SIZE = 100;
    private EditText sv_ip, sv_port;
    private String et_smsg;     //보낼 메세지
    private TextView tv_rmsg;   //받은 메세지
    private Socket socket;
    private DataOutputStream writeSocket;
    private DataInputStream readSocket;
    private Handler mHandler = new Handler();
    private ConnectivityManager cManager;
    private NetworkInfo wifi;
    private ServerSocket serverSocket;

    String google_busstop;
    String google_busstop_lat;
    String google_busstop_lng;
    String google_bus_num;
    String key="비공개KEY";
    String CITYCODE="33010";
    String routeId;
    String busstop_lati;
    String busstop_long;
    int index_result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub);


        //타이틀바 지우기
        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

//        text1= (TextView)findViewById(R.id.tv1);
        text2= (TextView)findViewById(R.id.tv2);
        text3= (TextView)findViewById(R.id.tv3);
//        tv_test=(TextView)findViewById(R.id.tv_test);

        //통신 코드
//        tv_rmsg = (TextView) findViewById(R.id.tv_rmsg);
        sv_ip = (EditText) findViewById(R.id.et_ip);
        sv_port = (EditText) findViewById(R.id.et_port);
        cManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);


        //TTS(TextToSpeech) 초기 설정
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });



        //이전 intent에서 버스정류장 정보, 버스번호 받아오기
        Intent intent=getIntent();
        google_busstop=intent.getStringExtra("busstop");
        google_busstop_lat=intent.getStringExtra("busstop_lat");
        google_busstop_lng=intent.getStringExtra("busstop_lng");
        google_bus_num=intent.getStringExtra("busnum");

        tv_test.setText("이전Intent: "+google_busstop+ ": "+ google_busstop_lat+", "+google_busstop_lng+" / "+google_bus_num+"번");

        bus();  //버스관련 공공 API 실행 함수

    }

// 통신코드 ~

    @SuppressWarnings("deprecation")
    public void OnClick(View v) throws Exception {
        switch (v.getId()) {
            case R.id.btn_connect:
                (new Connect()).start();
                break;
//            case R.id.btn_disconnect:
//                (new Disconnect()).start();
//                break;
//            case R.id.btn_send:
//                (new sendMessage()).start();
//                break;
            case R.id.btn_ride:
                (new send_ride()).start();
                break;
        }
    }

    class Connect extends Thread {

        public void run() {
            Log.d("Connect", "Run Connect");
            String ip = null;

            int port = 0;

            try {
                ip = sv_ip.getText().toString();
                port = Integer.parseInt(sv_port.getText().toString());
            } catch (Exception e) {
                final String recvInput = "정확히 입력하세요!";
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        tv_rmsg.setText(recvInput);
                    }
                });
            }
            try {
                socket = new Socket(ip, port);
                writeSocket = new DataOutputStream(socket.getOutputStream());
                readSocket = new DataInputStream(socket.getInputStream());

                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        tv_rmsg.setText("(연결에 성공하였습니다.)");

                    }

                });
                (new recvSocket()).start();

            } catch (Exception e) {
                final String recvInput = "(연결에 실패하였습니다.)";
                Log.d("Connect", e.getMessage());
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        tv_rmsg.setText(recvInput);
                    }

                });

            }

        }
    }

    class Disconnect extends Thread {
        public void run() {
            try {
                if (socket != null) {
                    socket.close();
                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            // TODO Auto-generated method stub
                            tv_rmsg.setText("(연결이 종료되었습니다.)");
                        }
                    });

                }

            } catch (Exception e) {
                final String recvInput = "연결에 실패하였습니다.";
                Log.d("Connect", e.getMessage());
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        tv_rmsg.setText(recvInput);
                    }

                });

            }

        }
    }

    //1: "탑승에 실패하였습니다" -> 버스번호판 다시 찾음
    //2: 버스 찾음 -> 문 인식 정보 기다림
    class recvSocket extends Thread {

        public void run() {
            try {
                readSocket = new DataInputStream(socket.getInputStream());

                while (true) {
                    byte[] b = new byte[100];
                    int ac = readSocket.read(b, 0, b.length);
                    String input = new String(b, 0, b.length);
                    final String recvInput = input.trim();

                    if(ac==-1)
                        break;

                    mHandler.post(new Runnable() {

                        @Override
                        public void run() {
                            // TODO Auto-generated method stub
                            tv_rmsg.setText(recvInput);

                            if(recvInput.equals("1")){
                                //탑승실패->번호다시찾음
                                tts.speak("탑승에 실패하였습니다. 다음 버스를 기다려주세요.",TextToSpeech.QUEUE_FLUSH, null);
                            }
                            else if(recvInput.equals("2")){
                                //탑승성공->문정보기다림
                                tts.speak("버스가 도착했습니다.",TextToSpeech.QUEUE_FLUSH, null);

                            }
                            else if(recvInput.equals("3")){
                                //10시방향
                                tts.speak("10시 방향에 버스 문이 있습니다.",TextToSpeech.QUEUE_FLUSH, null);
                            }
                            else if(recvInput.equals("4")){
                                //11시방향
                                tts.speak("11시 방향에 버스 문이 있습니다.",TextToSpeech.QUEUE_FLUSH, null);
                            }
                            else if(recvInput.equals("5")){
                                //12시방향
                                tts.speak("12시 방향에 버스 문이 있습니다.",TextToSpeech.QUEUE_FLUSH, null);
                            }
                            else if(recvInput.equals("6")){
                                //1시방향
                                tts.speak("1시 방향에 버스 문이 있습니다.",TextToSpeech.QUEUE_FLUSH, null);
                            }
                            else if(recvInput.equals("7")){
                                //2시방향
                                tts.speak("2시 방향에 버스 문이 있습니다.",TextToSpeech.QUEUE_FLUSH, null);
                            }
                            else{
                                //잘못된 값
                                tts.speak("알 수 없는 오류가 발생했습니다. 어플을 다시 실행해주세요.",TextToSpeech.QUEUE_FLUSH, null);
                            }
                        }

                    });
                }
                mHandler.post(new Runnable(){

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        tv_rmsg.setText("연결이 종료되었습니다.");

                    }

                });
            } catch (Exception e) {
                final String recvInput = "연결에 문제가 발생하여 종료되었습니다..";
                Log.d("SetServer", e.getMessage());
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        tv_rmsg.setText(recvInput);
                    }

                });

            }

        }
    }

    //버스 번호판 송신(시연용)
    class sendMessage extends Thread {
        int send_num;
        public void run() {
            try {
                byte[] b = new byte[100];
                b = new byte[100];
//                b = LicenseplateNum.getBytes();
                b = "8768".getBytes();

                writeSocket.write(b);


            } catch (Exception e) {
                final String recvInput = "메시지 전송에 실패하였습니다.";
                Log.d("SetServer", e.getMessage());
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        tv_rmsg.setText(recvInput);

                    }

                });

            }

        }
    }

    //버스 탑승 버튼 클릭 시 0 send
    class send_ride extends Thread {
        int send_num;
        public void run() {
            try {
                byte[] b = new byte[100];
                b = new byte[100];

                b = "0".getBytes();
                writeSocket.write(b);


            } catch (Exception e) {
                final String recvInput = "메시지 전송에 실패하였습니다.";
                Log.d("SetServer", e.getMessage());
                mHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        // TODO Auto-generated method stub
                        tv_rmsg.setText(recvInput);

                    }

                });

            }

        }
    }
// ~ 통신코드...


    //버스관련 공공API 실행 함수
    void bus(){
        new Thread(new Runnable() {
            @Override
            public void run() {

                busstop_ID=getXmlData_BusStopID();
                getXmlData_BusInfo();
                findXmlData_BusInService(); //버스정류장, 버스번호판 찾아 변수에 입력
                findXmlData_BusStopsOnRoute(); //해당 버스번호의 버스노선정보(지나가는 버스정류장)


                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
//                        text1.setText("정류소ID: "+busstop_ID+"  "+CITYCODE+ "\n"); //테스트코드
                        text2.setText(bus_arrInfo+"\n");
                        tts.speak(bus_arrInfo,TextToSpeech.QUEUE_FLUSH, null);

                        //같은 정류장 확인
//                        for (int i = 0; i < BusStops_arr1.size(); i++) {
//                            for(int j=0;j<BusStops_arr2.size();j++){
//                                if (BusStops_arr1.get(i).equals(BusStops_arr2.get(j))) {
//                                    if(busstop_ID==BusStops_arr2.get(j-1)) { //내 버스 정류장의 전 정류장이면
//                                        text3.setText("번호판: "+LicenseplateNum+"\n");
//                                    }
//                                }
//                            }
//                        }





                    }
                });
            }
        }).start();
    }

    //get 정류소ID to String
    String getXmlData_BusStopID(){
        // 이전intent 구글맵길찾기API에서 받아온 경도위도 값과 가장 가까운 정류소 ID알아내기
        // 국토교통부_(TAGO)_버스정류소정보 활용
        String citycode="";
        String busstop="";
        String nodeID="";
        String returnText="";

        String queryUrl=
        "http://apis.data.go.kr/1613000/BusSttnInfoInqireService/getCrdntPrxmtSttnList?" +
                        "serviceKey=" + key +
                "&pageNo=1&numOfRows=1&_type=xml" +
                "&gpsLati=" + google_busstop_lat +
                        "&gpsLong=" + google_busstop_lng;

        try{
            URL url= new URL(queryUrl);//문자열로 된 요청 url을 URL 객체로 생성.
            InputStream is= url.openStream(); //url위치로 입력스트림 연결

            XmlPullParserFactory factory= XmlPullParserFactory.newInstance();//xml파싱을 위한
            XmlPullParser xpp= factory.newPullParser();
            xpp.setInput( new InputStreamReader(is, "UTF-8") ); //inputstream 으로부터 xml 입력받기

            String tag;

            xpp.next();
            int eventType= xpp.getEventType();
            while( eventType != XmlPullParser.END_DOCUMENT ){
                switch( eventType ){
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        tag= xpp.getName();//테그 이름 얻어오기

                        if(tag.equals("item")) ;// 첫번째 검색결과
                        else if(tag.equals("citycode")){
                            //도시코드
                            xpp.next();
                            CITYCODE = xpp.getText();
                        }
                        else if(tag.equals("gpslati")){
                            xpp.next();
                            busstop_lati=xpp.getText();
                        }
                        else if(tag.equals("gpslong")){
                            xpp.next();
                            busstop_long=xpp.getText();
                        }
                        else if(tag.equals("nodeid")){
                            //정류소ID
                            xpp.next();
                            nodeID=xpp.getText();
                        }
                        else if(tag.equals("nodenm")){
                            //정류소 명
                            xpp.next();
                            busstop = xpp.getText();
                        }
                        else if(tag.equals("nodeno")){
                        }

                        break;

                    case XmlPullParser.TEXT:
                        break;

                    case XmlPullParser.END_TAG:
                        tag= xpp.getName(); //테그 이름 얻어오기
                        break;

                }

                eventType= xpp.next();
            }

        } catch (Exception e){
            e.printStackTrace();
        }

        return nodeID;

    }//getXmlData_BusStopID method....


    void getXmlData_BusInfo(){

        /***********************************************************
         * pageNo: 페이지번호
         * numOfRows:한 페이지 결과 수
         * _type: 데이터 타입(xml, json)
         * cityCode: 도시코드 [상세기능3 도시코드 목록 조회]에서 조회 가능
         * nodeId: 정류소ID [국토교통부(TAGO)_버스정류소정보]에서 조회가능
         ***********************************************************/

        String arrtime = "";
        String routeno = "";
        String routetp = "";
        String vehicletp = "";

        String queryUrl=
                "http://apis.data.go.kr/1613000/ArvlInfoInqireService/getSttnAcctoArvlPrearngeInfoList?" +
                        "serviceKey=" + key +
                        "&pageNo=1&numOfRows=50&_type=xml" +
                        "&cityCode=" + CITYCODE +
                        "&nodeId=" + busstop_ID;

        try{
            URL url= new URL(queryUrl);//문자열로 된 요청 url을 URL 객체로 생성.

            InputStream is= url.openStream(); //url위치로 입력스트림 연결

            XmlPullParserFactory factory= XmlPullParserFactory.newInstance();//xml파싱을 위한
            XmlPullParser xpp= factory.newPullParser();
            xpp.setInput( new InputStreamReader(is, "UTF-8") ); //inputstream 으로부터 xml 입력받기

            String tag;

            xpp.next();
            int eventType= xpp.getEventType();
            while( eventType != XmlPullParser.END_DOCUMENT ){
                switch( eventType ){
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        tag= xpp.getName();//tag 이름 얻어오기

                        if(tag.equals("item")) ;
                        else if(tag.equals("arrprevstationcnt")){ }
                        else if(tag.equals("arrtime")){
                            //도착시간[초]
                            xpp.next();
                            arrtime=xpp.getText();
                        }
                        else if(tag.equals("nodeid")){ }
                        else if(tag.equals("nodenm")){ }
                        else if(tag.equals("routeid")){
                            //노선ID
                            xpp.next();
                            routeId=(xpp.getText());
                        }
                        else if(tag.equals("routeno")){
                            //노선 번호
                            xpp.next();
                            routeno=xpp.getText();
                        }
                        else if(tag.equals("routetp")){
                            //노선유형
                            xpp.next();
                            routetp=xpp.getText();

                        }
                        else if(tag.equals("vehicletp")){

                            //차량유형
                            xpp.next();
                            vehicletp=xpp.getText();


                            if(routeno.equals(google_bus_num)){
                            bus_arrInfo="도착예상시간이 "+arrtime+"초 후인 "+routeno+"번 버스가 도착 예정입니다.";
                            }

                        }
                        break;

                    case XmlPullParser.TEXT:
                        break;

                    case XmlPullParser.END_TAG:
                        tag= xpp.getName(); //테그 이름 얻어오기
                        break;
                }

                eventType= xpp.next();

            }

        } catch (Exception e){
            e.printStackTrace();
        }

//        return result;

    }//getXmlData_BusInfo method....

    //현재 해당 노선에서 운행하고 있는 버스들의 정보(번호판 등)를 받아온다.
    void findXmlData_BusInService(){

        String queryUrl=
                "http://apis.data.go.kr/1613000/BusLcInfoInqireService/getRouteAcctoBusLcList?" +
                "serviceKey=" +key+
                "&pageNo=1&numOfRows=10&_type=xml" +
                "&cityCode=" + CITYCODE +
                "&routeId=" + routeId
                ;

        try{
            URL url= new URL(queryUrl);//문자열로 된 요청 url을 URL 객체로 생성.
            InputStream is= url.openStream(); //url위치로 입력스트림 연결

            XmlPullParserFactory factory= XmlPullParserFactory.newInstance();//xml파싱을 위한
            XmlPullParser xpp= factory.newPullParser();
            xpp.setInput( new InputStreamReader(is, "UTF-8") ); //inputstream 으로부터 xml 입력받기

            String tag;

            xpp.next();
            int eventType= xpp.getEventType();
            while( eventType != XmlPullParser.END_DOCUMENT ){
                switch( eventType ){
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        tag= xpp.getName();//테그 이름 얻어오기

                        if(tag.equals("item")) ;// 첫번째 검색결과
                        else if(tag.equals("gpslati")){ }
                        else if(tag.equals("gpslong")){ }
                        else if(tag.equals("nodeid")){
                            //정류소ID
                            xpp.next();
                            BusStops_arr1.add(xpp.getText());
                        }
                        else if(tag.equals("nodenm")){ }
                        else if(tag.equals("nodeord")){ }
                        else if(tag.equals("routenm")){ }
                        else if(tag.equals("vehicleno")){
                            //차량번호(번호판)
                            xpp.next();
                            BusesPlateNum_arr.add(xpp.getText());
                        }
                        break;

                    case XmlPullParser.TEXT:
                        break;

                    case XmlPullParser.END_TAG:
                        tag= xpp.getName(); //테그 이름 얻어오기
                        break;
                }

                eventType= xpp.next();

            }


        } catch (Exception e){
            e.printStackTrace();
        }

    }//findXmlData_BusInService method....

    //get 노선정보 to string 배열
    void findXmlData_BusStopsOnRoute() {

        //지난 노선 정보 중 일치하는 정류장의 다음 정류장을 리턴
        //현재 내가 서 있는 버스정류장과 오고 있는 버스 중에서 가장 가까운 버스 번호판 정보를 받아와야함.

        String queryUrl =
                "http://apis.data.go.kr/1613000/BusRouteInfoInqireService/getRouteAcctoThrghSttnList?" +
                        "serviceKey=" +key+
                        "&pageNo=1&numOfRows=100&_type=xml" +
                        "&cityCode=" + CITYCODE +
                        "&routeId=" +routeId
                ;

        try {
            URL url = new URL(queryUrl);//문자열로 된 요청 url을 URL 객체로 생성.
            InputStream is = url.openStream(); //url위치로 입력스트림 연결

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();//xml파싱을 위한
            XmlPullParser xpp = factory.newPullParser();
            xpp.setInput(new InputStreamReader(is, "UTF-8")); //inputstream 으로부터 xml 입력받기

            String tag;

            xpp.next();
            int eventType = xpp.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        tag = xpp.getName();//tag 이름 얻어오기

                        if (tag.equals("item")) ;
                        else if (tag.equals("gpslati")) { }
                        else if (tag.equals("gpslong")) { }
                        else if (tag.equals("nodeid")) {
                            //정류소ID
                            xpp.next();
                            BusStops_arr2.add(xpp.getText());
                        }
                        else if (tag.equals("nodenm")) { }
                        else if (tag.equals("nodeno")) { }
                        else if (tag.equals("nodeord")) { }
                        else if (tag.equals("routeid")) { }
                        else if (tag.equals("updowncd")) { }
                        break;

                    case XmlPullParser.TEXT:
                        break;

                    case XmlPullParser.END_TAG:
                        tag = xpp.getName(); //tag 이름 얻어오기
                        break;
                }

                eventType = xpp.next();

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }//findXmlData_BusStopsOnRoute method....

}
package com.sinovotec.sinovolibdemo.ui.slideshow;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.qmuiteam.qmui.widget.roundwidget.QMUIRoundButton;
import com.sinovotec.andesptouch.AndEsptouch;
import com.sinovotec.andesptouch.AndEsptouchHelper;
import com.sinovotec.sinovolibdemo.R;

public class SlideshowFragment extends Fragment implements AndEsptouch.OnEsptouchTaskListener {

    private AndEsptouch andEsptouch;
    private EditText wifiSSID;
    private EditText wifiPass;
    // private CountDownTimer countDownTimer;
    private final String TAG = "SinovoLib";

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_slideshow, container, false);
        wifiSSID = root.findViewById(R.id.ssidET);
        wifiPass = root.findViewById(R.id.passET);
        QMUIRoundButton qmuiRoundButton = root.findViewById(R.id.connect1);

        qmuiRoundButton.setOnClickListener(view -> {
            String wifissid = wifiSSID.getText().toString();
            String wifipass = wifiPass.getText().toString();

            if (!wifissid.isEmpty() && !wifipass.isEmpty()){

                //esp方案的 一键配网
                String bssid = AndEsptouchHelper.getInstance(getContext()).getBSSID();

                andEsptouch = new AndEsptouch.Builder(getContext())
                        .setSSID(wifissid)
                        .setBSSID(bssid)
                        .setPassWord(wifipass)
                        .build();
                andEsptouch.startConfig();
                andEsptouch.setOnEsptouchTaskListener(this);

                Log.d(TAG, "start config：ssid:"+ wifissid + ",bssid:"+ bssid + ",pass:"+ wifipass);

//                if (countDownTimer != null) {
//                    countDownTimer = null;
//                }
//                startCountDown();
            }
        });

        return root;
    }

//    private void startCountDown() {
//        // showProgressDialog("努力配网中...");
//        Log.d(TAG, "努力配网中..." );
//        countDownTimer = new CountDownTimer(20000, 1000) {
//            @Override
//            public void onTick(long millisUntilFinished) {
//                Log.d(TAG, "回调  startCountDown  onTick:" + millisUntilFinished);
//            }
//
//            @Override
//            public void onFinish() {
//                Log.d(TAG, "回调  startCountDown  onFinish");
//            }
//        };
//    }

    @Override
    public void onEsptouchTaskCallback(int code, String message) {
        Log.d(TAG, "smartconfig finish, code:" + code + "message:" + message);

        if (code == AndEsptouch.RESULT_CONFIG_SUCCESS) {
            if (andEsptouch!=null){ andEsptouch.stopConfig(); }
            if (message.indexOf("BSSID:") == 0){
                String gatewayID = message.substring(6,18).toUpperCase();
                Log.d(TAG,"Gateway configure network ok");

                String gwsuccess = "Success! Gateway id:" + gatewayID;
                Toast.makeText(getActivity(), gwsuccess, Toast.LENGTH_SHORT).show();
            }
        } else if (code == AndEsptouch.RESULT_CONFIG_TIMEOUT) {
            Log.d(TAG,  "Failed to configure network");
            Toast.makeText(getActivity(), getResources().getString(R.string.failedgw), Toast.LENGTH_SHORT).show();
        } else {
            Log.d(TAG,  "Failed to configure network");
            Toast.makeText(getActivity(), getResources().getString(R.string.failedgw), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (andEsptouch!=null){
            andEsptouch.stopConfig();
            andEsptouch.cancelAsyncTask();
        }
    }

}
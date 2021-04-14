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
import com.sinovotec.gwSmartConfig.GWSmartConfig;
import com.sinovotec.gwSmartConfig.GWSmartConfigCallback;
import com.sinovotec.sinovolibdemo.R;

public class SlideshowFragment extends Fragment {

    private EditText wifiSSID;
    private EditText wifiPass;
    private GWSmartConfigCallback gwSmartConfigCallback;
    private final String TAG = "SinovoLib";

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_slideshow, container, false);
        wifiSSID = root.findViewById(R.id.ssidET);
        wifiPass = root.findViewById(R.id.passET);
        QMUIRoundButton qmuiRoundButton = root.findViewById(R.id.connect1);

        gwSmartConfigCallback = new GWSmartConfigCallback() {
            @Override
            public void onConfigSUCCESS(String msg) {
                Log.d(TAG,"Gateway configure network ok");

                String gwsuccess = "Success! Gateway id:" + msg;
                Toast.makeText(getActivity(), gwsuccess, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onConfigFailed(String msg) {
                Log.d(TAG,  "Failed to configure network");
                Toast.makeText(getActivity(), getResources().getString(R.string.failedgw), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onConfigTimeOut(String msg) {
                Log.d(TAG,  "Failed to configure network");
                Toast.makeText(getActivity(), getResources().getString(R.string.failedgw), Toast.LENGTH_SHORT).show();
            }
        };

        qmuiRoundButton.setOnClickListener(view -> {
            String wifissid = wifiSSID.getText().toString();
            String wifipass = wifiPass.getText().toString();

            if (!wifissid.isEmpty() && !wifipass.isEmpty()){
                GWSmartConfig.getInstance().startConifg(getContext(), gwSmartConfigCallback, wifissid, wifipass);
                Log.d(TAG, "start config：ssid:"+ wifissid +  ",pass:"+ wifipass);
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        GWSmartConfig.getInstance().exitConfig();
    }

}
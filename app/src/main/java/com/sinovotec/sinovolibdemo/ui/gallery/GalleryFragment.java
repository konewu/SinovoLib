package com.sinovotec.sinovolibdemo.ui.gallery;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.qmuiteam.qmui.widget.roundwidget.QMUIRoundButton;
import com.sinovotec.sinovoble.SinovoBle;
import com.sinovotec.sinovoble.common.BleConnectLock;

import com.sinovotec.sinovolibdemo.MyApp;
import com.sinovotec.sinovolibdemo.R;

import java.util.ArrayList;

public class GalleryFragment extends Fragment {

    private final String TAG = "SinovoLib";
    private EditText macET = null;
    private EditText snoET = null;
    private EditText codeET = null;
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_gallery, container, false);

        macET = root.findViewById(R.id.macET);
        snoET = root.findViewById(R.id.snoET);
        codeET = root.findViewById(R.id.codeET);
        final QMUIRoundButton connect1_btn = root.findViewById(R.id.connect1);
        final QMUIRoundButton btn_chkpw = root.findViewById(R.id.chkpower);
        final QMUIRoundButton btn_chkver = root.findViewById(R.id.chkver);

        MyApp.getInstance().setShowMacStatus(root.findViewById(R.id.statusTX));
        MyApp.getInstance().setMaclockBtn(root.findViewById(R.id.connect2));
        MyApp.getInstance().setShowMacResult(root.findViewById(R.id.showresult));

        MyApp.getInstance().setShowMaclockType(root.findViewById(R.id.firwwareet));
        MyApp.getInstance().setShowMaclockver(root.findViewById(R.id.versionet));
        MyApp.getInstance().setShowMaclockpower(root.findViewById(R.id.poweret));


        connect1_btn.setOnClickListener(view -> {
            if (macET.length() !=12 ){
                Toast.makeText(getActivity(), getResources().getString(R.string.mac_len), Toast.LENGTH_SHORT).show();
                return;
            }

            if (snoET.length() !=6 ){
                Toast.makeText(getActivity(), getResources().getString(R.string.sno_len), Toast.LENGTH_SHORT).show();
                return;
            }

            String mac = macWithColon(macET.getText().toString());
            String sno = snoET.getText().toString();

            MyApp.getInstance().setLockSno(sno);
            MyApp.getInstance().setLockMac(mac);

            ArrayList<BleConnectLock>  autoConnetList = new ArrayList<>();
            BleConnectLock newlock = new BleConnectLock(mac.toUpperCase(),sno);
            autoConnetList.add(newlock);
            SinovoBle.getInstance().connectLockViaMacSno(autoConnetList);

            showProgressDialog("Connecting....");
        });


        MyApp.getInstance().getMaclockBtn().setOnClickListener(view -> {
            String code = codeET.getText().toString();
            if (code.isEmpty()){
                Toast.makeText(getActivity(), getResources().getString(R.string.inputcode), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!SinovoBle.getInstance().isBleConnected()){
                Toast.makeText(getActivity(), getResources().getString(R.string.unconnect), Toast.LENGTH_SHORT).show();
                return;
            }

            String openType = "01";
            if (MyApp.getInstance().getLockStatus().equals("01")){
                openType = "00";
            }

            MyApp.getInstance().setCode(code);
            SinovoBle.getInstance().toUnlock(openType, code, MyApp.getInstance().getLockSno(), MyApp.getInstance().getLockMac());
        });

        btn_chkpw.setOnClickListener(view -> {
            if (!SinovoBle.getInstance().isBleConnected()){
                Toast.makeText(getActivity(), getResources().getString(R.string.unconnect), Toast.LENGTH_SHORT).show();
                return;
            }
            SinovoBle.getInstance().getLockInfo("02", MyApp.getInstance().getLockSno(), MyApp.getInstance().getLockMac());
        });



        btn_chkver.setOnClickListener(view -> {
            if (!SinovoBle.getInstance().isBleConnected()){
                Toast.makeText(getActivity(), getResources().getString(R.string.unconnect), Toast.LENGTH_SHORT).show();
                return;
            }
            SinovoBle.getInstance().getLockInfo("04", MyApp.getInstance().getLockSno(), MyApp.getInstance().getLockMac());
        });

        return root;
    }

    /**
     * 转换mac地址，将 00A051F4DC4C转换为00:A0:51:F4:DC:4C
     */
    public static String macWithColon(String macAddress) {
        StringBuilder lockMac = new StringBuilder();
        for (int j = 0; j < macAddress.length(); ) {
            if (j < macAddress.length() - 2) {
                lockMac.append(macAddress.substring(j, j + 2)).append(":");
            } else {
                lockMac.append(macAddress.substring(j, j + 2));
            }
            j = j + 2;
        }
        return lockMac.toString().toUpperCase();
    }

    //显示 进度dialog
    private void showProgressDialog(String msg){
        if (MyApp.getInstance().getProgressDialog() == null) {
            MyApp.getInstance().setProgressDialog(new ProgressDialog(MyApp.getInstance().getMyContext()));
            MyApp.getInstance().getProgressDialog().setProgressStyle(ProgressDialog.STYLE_SPINNER);
        }
        MyApp.getInstance().getProgressDialog().setMessage(msg);
        MyApp.getInstance().getProgressDialog().setCancelable(true);
        MyApp.getInstance().getProgressDialog().show();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "GalleryFragment onResume");
        if (SinovoBle.getInstance().isBleConnected()){
            MyApp.getInstance().getShowStatus().setText("Connected");
            MyApp.getInstance().getShowStatus().setTextColor(ContextCompat.getColor(MyApp.getInstance().getMyContext(),R.color.colorBackground_green));

            MyApp.getInstance().getShowMacStatus().setText("Connected");
            MyApp.getInstance().getShowMacStatus().setTextColor(ContextCompat.getColor(MyApp.getInstance().getMyContext(),R.color.colorBackground_green));
        }

        MyApp.getInstance().getCodeET().setText(MyApp.getInstance().getCode());
        MyApp.getInstance().getShowMaclockpower().setText(MyApp.getInstance().getLockpower());
        MyApp.getInstance().getShowMaclockver().setText(MyApp.getInstance().getLockversion());
        MyApp.getInstance().getShowMaclockType().setText(MyApp.getInstance().getLocktype());

    }
}
package cc.ytdttj.speed;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    // 定位模式常量
    public static final String PREF_NAME = "SpeedSettings";
    public static final String KEY_LOCATION_MODE = "location_mode";
    public static final int MODE_HIGH_ACCURACY = 0;
    public static final int MODE_BALANCED = 1;
    
    // 电量阈值
    public static final int LOW_BATTERY_THRESHOLD = 20;
    
    private RadioGroup locationModeGroup;
    private RadioButton highAccuracyMode;
    private RadioButton balancedMode;
    private Button saveSettingsButton;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // 初始化视图
        initViews();
        
        // 加载当前设置
        loadCurrentSettings();
        
        // 检查电池电量
        checkBatteryLevel();
        
        // 设置保存按钮点击事件
        saveSettingsButton.setOnClickListener(v -> {
            saveSettings();
            finish();
        });
    }
    
    private void initViews() {
        locationModeGroup = findViewById(R.id.location_mode_group);
        highAccuracyMode = findViewById(R.id.high_accuracy_mode);
        balancedMode = findViewById(R.id.balanced_mode);
        saveSettingsButton = findViewById(R.id.save_settings_button);
    }
    
    private void loadCurrentSettings() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int currentMode = prefs.getInt(KEY_LOCATION_MODE, MODE_HIGH_ACCURACY);
        
        if (currentMode == MODE_HIGH_ACCURACY) {
            highAccuracyMode.setChecked(true);
        } else {
            balancedMode.setChecked(true);
        }
    }
    
    private void checkBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        
        float batteryPct = level * 100 / (float)scale;
        
        // 如果电量低于阈值，强制选择平衡模式并禁用高精度模式
        if (batteryPct < LOW_BATTERY_THRESHOLD) {
            balancedMode.setChecked(true);
            highAccuracyMode.setEnabled(false);
            Toast.makeText(this, "电量低于20%，已自动切换到平衡模式以节省电量", Toast.LENGTH_LONG).show();
        }
    }
    
    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        int selectedMode = highAccuracyMode.isChecked() ? MODE_HIGH_ACCURACY : MODE_BALANCED;
        editor.putInt(KEY_LOCATION_MODE, selectedMode);
        editor.apply();
        
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 获取当前定位模式
     * @param context 上下文
     * @return 定位模式（MODE_HIGH_ACCURACY 或 MODE_BALANCED）
     */
    public static int getLocationMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_LOCATION_MODE, MODE_HIGH_ACCURACY);
    }
    
    /**
     * 检查是否应该使用平衡模式（基于电量或用户设置）
     * @param context 上下文
     * @return 是否应该使用平衡模式
     */
    public static boolean shouldUseBalancedMode(Context context) {
        // 获取用户设置的模式
        int userMode = getLocationMode(context);
        
        // 如果用户已经选择了平衡模式，直接返回true
        if (userMode == MODE_BALANCED) {
            return true;
        }
        
        // 检查电池电量
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        
        float batteryPct = level * 100 / (float)scale;
        
        // 如果电量低于阈值，即使用户选择了高精度模式，也返回平衡模式
        return batteryPct < LOW_BATTERY_THRESHOLD;
    }
}
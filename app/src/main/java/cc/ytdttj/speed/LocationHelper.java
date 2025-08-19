package cc.ytdttj.speed;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 位置服务助手类，提供高精度和平衡模式的定位功能
 */
public class LocationHelper {
    private static final String TAG = "LocationHelper";
    
    // 定位提供者
    private static final String PROVIDER_GPS = LocationManager.GPS_PROVIDER;
    private static final String PROVIDER_NETWORK = LocationManager.NETWORK_PROVIDER;
    
    // GPS更新频率（毫秒）- 进一步优化为极快的刷新频率
    private static final long GPS_UPDATE_INTERVAL_SEARCHING = 100; // 搜星中的更新频率 - 优化到100ms
    private static final long GPS_UPDATE_INTERVAL_CONNECTED = 100; // 已连接的更新频率 - 保持100ms
    private static final long GPS_UPDATE_INTERVAL_STABLE = 100; // 稳定连接的更新频率 - 优化到100ms
    private static final long NETWORK_UPDATE_INTERVAL = 500; // 网络定位更新频率 - 优化到500ms
    
    // 最小距离变化（米）
    private static final float MIN_DISTANCE_CHANGE = 0;
    
    private Context context;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private GnssStatus.Callback gnssStatusCallback;
    private Handler satelliteStatusHandler = new Handler(Looper.getMainLooper());
    private int lastSentSatelliteCount = -1;
    private int lastSentSatellitesUsedInFix = -1;
    private static final long SATELLITE_STATUS_UPDATE_DELAY = 1500; // 1.5秒延迟
    private static final int SATELLITE_COUNT_STABILITY_THRESHOLD = 2; // 2颗卫星的波动阈值
    private Handler handler;
    
    // 状态变量
    private boolean isGpsConnected = false;
    private boolean isUsingBalancedMode = false;
    private boolean isGpsEnabled = false;
    private boolean isNetworkEnabled = false;
    
    // 卫星信息
    private int satelliteCount = 0;
    private int satellitesInFix = 0;
    private int stableSatelliteCount = 0; // 稳定连接的卫星数量
    private long lastGoodConnectionTime = 0; // 上次良好连接的时间
    private boolean hasStableConnection = false; // 是否有稳定连接

    // 静止状态检测
    private static final float STATIONARY_SPEED_THRESHOLD = 0.5f; // m/s
    private static final long STATIONARY_TIME_THRESHOLD = 3000; // ms
    private long stationaryStartTime = 0;
    private boolean isStationary = false;
    
    // 最后获取的位置
    private Location lastGpsLocation;
    private Location lastNetworkLocation;
    
    // 回调接口
    private OnLocationUpdateListener locationUpdateListener;
    private OnGpsStatusChangeListener gpsStatusChangeListener;
    
    public LocationHelper(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }
    
    /**
     * 初始化定位服务
     * @param locationListener 位置更新监听器
     * @param gpsStatusListener GPS状态变化监听器
     */
    public void initialize(OnLocationUpdateListener locationListener, OnGpsStatusChangeListener gpsStatusListener) {
        this.locationUpdateListener = locationListener;
        this.gpsStatusChangeListener = gpsStatusListener;
        
        // 检查定位模式
        updateLocationMode();
        
        // 初始化位置监听器
        initLocationListener();
        
        // 初始化GNSS状态回调
        initGnssStatusCallback();
        
        // 开始定位
        startLocationUpdates();
    }
    
    /**
     * 更新定位模式（高精度或平衡模式）
     */
    public void updateLocationMode() {
        isUsingBalancedMode = SettingsActivity.shouldUseBalancedMode(context);
        Log.d(TAG, "定位模式: " + (isUsingBalancedMode ? "平衡模式" : "高精度模式"));
        
        // 如果已经初始化了定位，则重新启动定位以应用新模式
        if (locationListener != null) {
            stopLocationUpdates();
            startLocationUpdates();
        }
    }
    
    /**
     * 初始化位置监听器
     */
    private void initLocationListener() {
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                processNewLocation(location);
            }
            
            @Override
            public void onProviderEnabled(@NonNull String provider) {
                if (provider.equals(PROVIDER_GPS)) {
                    isGpsEnabled = true;
                } else if (provider.equals(PROVIDER_NETWORK)) {
                    isNetworkEnabled = true;
                }
                updateProviderStatus();
            }
            
            @Override
            public void onProviderDisabled(@NonNull String provider) {
                if (provider.equals(PROVIDER_GPS)) {
                    isGpsEnabled = false;
                } else if (provider.equals(PROVIDER_NETWORK)) {
                    isNetworkEnabled = false;
                }
                updateProviderStatus();
            }
        };
    }
    
    /**
     * 初始化GNSS状态回调
     */
    private void initGnssStatusCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            gnssStatusCallback = new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    super.onSatelliteStatusChanged(status);
                    satelliteCount = status.getSatelliteCount();
                    satellitesInFix = 0;
                    for (int i = 0; i < satelliteCount; i++) {
                        if (status.usedInFix(i)) {
                            satellitesInFix++;
                        }
                    }

                    // 更新GPS连接状态和稳定性
                    boolean wasConnected = isGpsConnected;
                    updateGpsConnectionStability();

                    // 如果GPS连接状态发生变化，更新GPS更新频率
                    if (wasConnected != isGpsConnected) {
                        updateGpsUpdateInterval();
                    }

                    // 使用 Handler 去抖动卫星状态更新
                    satelliteStatusHandler.removeCallbacksAndMessages(null);
                    satelliteStatusHandler.postDelayed(() -> {
                        // 检查卫星数量变化是否超过阈值，或者是否是首次更新
                        if (Math.abs(satelliteCount - lastSentSatelliteCount) > SATELLITE_COUNT_STABILITY_THRESHOLD ||
                            Math.abs(satellitesInFix - lastSentSatellitesUsedInFix) > SATELLITE_COUNT_STABILITY_THRESHOLD ||
                            lastSentSatelliteCount == -1) {

                            if (gpsStatusChangeListener != null) {
                                gpsStatusChangeListener.onGpsStatusChanged(satellitesInFix, satelliteCount, isGpsConnected);
                            }
                            // 更新上次发送的值
                            lastSentSatelliteCount = satelliteCount;
                            lastSentSatellitesUsedInFix = satellitesInFix;
                        }
                    }, SATELLITE_STATUS_UPDATE_DELAY);
                }
            };
        }
    }
    
    /**
     * 开始位置更新
     */
    public void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "没有定位权限");
            return;
        }
        
        // 检查提供者是否可用
        isGpsEnabled = locationManager.isProviderEnabled(PROVIDER_GPS);
        isNetworkEnabled = locationManager.isProviderEnabled(PROVIDER_NETWORK);
        
        // 根据定位模式启动相应的定位提供者
        if (!isUsingBalancedMode) {
            // 高精度模式：同时使用GPS和网络定位
            startGpsProvider();
            startNetworkProvider();
        } else {
            // 平衡模式：根据GPS信号质量动态选择
            startGpsProvider();
            // 如果GPS信号不好，也启用网络定位
            if (!isGpsConnected || satellitesInFix < 4) {
                startNetworkProvider();
            }
        }
        
        // 注册GNSS状态回调
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            try {
                locationManager.registerGnssStatusCallback(gnssStatusCallback, handler);
            } catch (Exception e) {
                Log.e(TAG, "注册GNSS状态回调失败", e);
            }
        }
    }
    
    /**
     * 停止位置更新
     */
    public void stopLocationUpdates() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
        
        // 取消注册GNSS状态回调
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
        }
    }
    
    /**
     * 启动GPS提供者
     */
    private void startGpsProvider() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            long interval = isGpsConnected ? GPS_UPDATE_INTERVAL_CONNECTED : GPS_UPDATE_INTERVAL_SEARCHING;
            locationManager.requestLocationUpdates(PROVIDER_GPS, interval, MIN_DISTANCE_CHANGE, locationListener);
            Log.d(TAG, "启动GPS提供者，更新间隔: " + interval + "ms");
        }
    }
    
    /**
     * 启动网络定位提供者
     */
    private void startNetworkProvider() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(PROVIDER_NETWORK, NETWORK_UPDATE_INTERVAL, MIN_DISTANCE_CHANGE, locationListener);
            Log.d(TAG, "启动网络定位提供者，更新间隔: " + NETWORK_UPDATE_INTERVAL + "ms");
        }
    }
    
    /**
     * 更新GPS更新频率
     */
    private void updateGpsUpdateInterval() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // 先移除当前的GPS更新
            locationManager.removeUpdates(locationListener);
            
            // 根据连接稳定性选择更新频率 - 统一使用100ms高频更新
            long interval;
            if (hasStableConnection && stableSatelliteCount >= 6) {
                // 稳定连接且卫星数充足，使用100ms高频更新保证时效性
                interval = GPS_UPDATE_INTERVAL_STABLE;
                Log.d(TAG, "使用稳定连接模式，更新频率: " + interval + "ms，稳定卫星数: " + stableSatelliteCount);
            } else if (isGpsConnected) {
                // 有连接但不够稳定，使用100ms高频更新
                interval = GPS_UPDATE_INTERVAL_CONNECTED;
                Log.d(TAG, "使用标准连接模式，更新频率: " + interval + "ms");
            } else {
                // 搜星模式，使用100ms高频更新快速建立连接
                interval = GPS_UPDATE_INTERVAL_SEARCHING;
                Log.d(TAG, "使用搜星模式，更新频率: " + interval + "ms");
            }
            
            locationManager.requestLocationUpdates(PROVIDER_GPS, interval, MIN_DISTANCE_CHANGE, locationListener);
            
            // 如果在平衡模式下，根据GPS状态决定是否使用网络定位
            if (isUsingBalancedMode) {
                if (!hasStableConnection || satellitesInFix < 4) {
                    startNetworkProvider();
                } else {
                    // 有稳定GPS连接时，可以减少网络定位的使用
                    Log.d(TAG, "GPS连接稳定，减少网络定位依赖");
                }
            }
        }
    }
    
    /**
     * 更新GPS连接稳定性
     */
    private void updateGpsConnectionStability() {
        long currentTime = System.currentTimeMillis();
        
        // 检查当前连接状态
        boolean currentlyConnected = satellitesInFix >= 4; // 至少4颗卫星才算稳定连接
        
        if (currentlyConnected) {
            // 如果当前连接良好
            if (!hasStableConnection) {
                // 首次建立稳定连接
                if (satellitesInFix >= 6) {
                    // 6颗或以上卫星，立即认为稳定
                    hasStableConnection = true;
                    stableSatelliteCount = satellitesInFix;
                    lastGoodConnectionTime = currentTime;
                    Log.d(TAG, "建立稳定GPS连接，卫星数: " + satellitesInFix);
                } else if (lastGoodConnectionTime == 0) {
                    // 记录首次连接时间
                    lastGoodConnectionTime = currentTime;
                } else if (currentTime - lastGoodConnectionTime > 10000) {
                    // 连续10秒保持4-5颗卫星连接，认为稳定
                    hasStableConnection = true;
                    stableSatelliteCount = satellitesInFix;
                    Log.d(TAG, "经过验证建立稳定GPS连接，卫星数: " + satellitesInFix);
                }
            } else {
                // 已有稳定连接，更新状态
                lastGoodConnectionTime = currentTime;
                
                // 如果卫星数量显著增加，更新稳定卫星数
                if (satellitesInFix > stableSatelliteCount + 2) {
                    stableSatelliteCount = satellitesInFix;
                    Log.d(TAG, "稳定连接卫星数增加到: " + satellitesInFix);
                }
            }
            
            isGpsConnected = true;
        } else {
            // 当前连接不佳
            if (hasStableConnection) {
                // 有稳定连接但暂时信号不好
                if (currentTime - lastGoodConnectionTime < 30000) {
                    // 30秒内的信号中断，保持连接状态，避免频繁切换
                    isGpsConnected = true;
                    Log.d(TAG, "GPS信号暂时中断，保持连接状态，卫星数: " + satellitesInFix);
                } else {
                    // 超过30秒信号不好，认为连接丢失
                    hasStableConnection = false;
                    isGpsConnected = false;
                    stableSatelliteCount = 0;
                    Log.d(TAG, "GPS连接丢失，重新搜星");
                }
            } else {
                // 没有稳定连接且当前连接不佳
                isGpsConnected = satellitesInFix > 0;
                if (!isGpsConnected) {
                    lastGoodConnectionTime = 0;
                }
            }
        }
    }
    
    /**
     * 更新提供者状态
     */
    private void updateProviderStatus() {
        if (gpsStatusChangeListener != null) {
            gpsStatusChangeListener.onProviderStatusChanged(isGpsEnabled, isNetworkEnabled);
        }
    }
    
    /**
     * 处理新的位置信息
     * @param location 新位置
     */
    private void processNewLocation(Location location) {
        if (location == null) return;

        // 过滤明显错误的位置数据
        if (!isLocationValid(location)) {
            // 如果位置无效，但我们之前处于静止状态，则允许速度归零
            if (isStationary) {
                location.setSpeed(0);
            } else {
                Log.d(TAG, "位置数据无效，已过滤: " + location.getProvider() +
                        ", 精度: " + location.getAccuracy() + "m");
                return;
            }
        }

        // 静止状态检测
        if (location.getSpeed() < STATIONARY_SPEED_THRESHOLD) {
            if (stationaryStartTime == 0) {
                stationaryStartTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - stationaryStartTime > STATIONARY_TIME_THRESHOLD) {
                isStationary = true;
            }
        } else {
            stationaryStartTime = 0;
            isStationary = false;
        }

        if (isStationary) {
            location.setSpeed(0);
        }
        
        String provider = location.getProvider();
        
        // 更新最后获取的位置
        if (PROVIDER_GPS.equals(provider)) {
            lastGpsLocation = location;
        } else if (PROVIDER_NETWORK.equals(provider)) {
            lastNetworkLocation = location;
        }
        
        // 选择最佳位置
        Location bestLocation = getBestLocation();
        
        // 更新lastValidLocation用于静止状态检测
        if (bestLocation != null) {
            // 如果是有效移动或首次获取位置，更新lastValidLocation
            if (lastValidLocation == null || 
                lastValidLocation.distanceTo(bestLocation) >= STATIONARY_RADIUS) {
                lastValidLocation = new Location(bestLocation);
                Log.d(TAG, "更新有效位置参考点，精度: " + bestLocation.getAccuracy() + "m");
            }
        }
        
        // 通知监听器
        if (bestLocation != null && locationUpdateListener != null) {
            locationUpdateListener.onLocationUpdated(bestLocation);
        }
    }
    
    // 静止状态检测参数
    private Location lastValidLocation = null;
    private long lastMovementTime = 0;
    private static final float STATIONARY_RADIUS = 5.0f; // 静止半径（米）
    private static final long STATIONARY_TIME_THRESHOLD = 3000; // 静止时间阈值（毫秒）
    
    /**
     * 验证位置数据的有效性
     * @param location 位置数据
     * @return 是否有效
     */
    private boolean isLocationValid(Location location) {
        // 检查基本有效性
        if (location.getLatitude() == 0.0 && location.getLongitude() == 0.0) {
            return false;
        }
        
        // 检查精度是否合理（大于100米的位置可能不准确）
        if (location.getAccuracy() > 100) {
            return false;
        }
        
        // 检查时间是否太旧（超过10秒的位置数据可能过时）
        long currentTime = System.currentTimeMillis();
        if (currentTime - location.getTime() > 10000) {
            return false;
        }
        
        // 检查速度是否合理（超过300km/h的速度可能是错误数据）
        if (location.hasSpeed() && location.getSpeed() > 83.33f) { // 300km/h = 83.33m/s
            return false;
        }
        
        // 静止状态检测：过滤GPS漂移
        if (lastValidLocation != null) {
            float distance = lastValidLocation.distanceTo(location);
            
            // 如果移动距离很小，可能是GPS漂移
            if (distance < STATIONARY_RADIUS) {
                // 检查是否在静止状态
                if (isInStationaryState(location)) {
                    // 在静止状态下，只接受精度更高的位置
                    return location.getAccuracy() < lastValidLocation.getAccuracy() - 2.0f;
                }
            } else {
                // 有明显移动，更新最后移动时间
                lastMovementTime = currentTime;
            }
        }
        
        return true;
    }
    
    /**
     * 检查是否处于静止状态
     * @param location 当前位置
     * @return 是否静止
     */
    private boolean isInStationaryState(Location location) {
        long currentTime = System.currentTimeMillis();
        
        // 如果速度很低且在静止半径内超过阈值时间，认为是静止状态
        boolean lowSpeed = !location.hasSpeed() || location.getSpeed() < 0.5f; // 小于1.8km/h
        boolean stationaryTime = (currentTime - lastMovementTime) > STATIONARY_TIME_THRESHOLD;
        
        return lowSpeed && stationaryTime;
    }
    
    /**
     * 获取最佳位置
     * @return 最佳位置
     */
    private Location getBestLocation() {
        // 如果只有一个位置可用，直接返回
        if (lastGpsLocation == null) return lastNetworkLocation;
        if (lastNetworkLocation == null) return lastGpsLocation;
        
        long currentTime = System.currentTimeMillis();
        float gpsAccuracy = lastGpsLocation.getAccuracy();
        float networkAccuracy = lastNetworkLocation.getAccuracy();
        long gpsTime = lastGpsLocation.getTime();
        long networkTime = lastNetworkLocation.getTime();
        
        // 检查位置数据的新鲜度
        boolean gpsIsFresh = (currentTime - gpsTime) < 5000; // GPS数据5秒内为新鲜
        boolean networkIsFresh = (currentTime - networkTime) < 10000; // 网络数据10秒内为新鲜
        
        // 如果在高精度模式下
        if (!isUsingBalancedMode) {
            // 优先使用新鲜的GPS数据
            if (gpsIsFresh) {
                return lastGpsLocation;
            }
            // 如果GPS数据不新鲜但网络数据新鲜且精度可接受，使用网络数据
            if (networkIsFresh && networkAccuracy < 50) {
                return lastNetworkLocation;
            }
            // 否则仍使用GPS数据（即使不够新鲜）
            return lastGpsLocation;
        }
        
        // 平衡模式下的智能选择策略
        
        // 1. 如果GPS连接良好且数据新鲜，优先使用GPS
        if (isGpsConnected && gpsIsFresh && gpsAccuracy < 20) {
            return lastGpsLocation;
        }
        
        // 2. 如果GPS精度明显更好且数据不太旧，使用GPS
        if (gpsAccuracy < networkAccuracy * 0.6 && (currentTime - gpsTime) < 15000) {
            return lastGpsLocation;
        }
        
        // 3. 如果网络位置更新且精度可接受，使用网络位置
        if (networkIsFresh && networkAccuracy < 100 && networkTime > gpsTime + 5000) {
            return lastNetworkLocation;
        }
        
        // 4. 根据综合评分选择最佳位置
        float gpsScore = calculateLocationScore(lastGpsLocation, currentTime);
        float networkScore = calculateLocationScore(lastNetworkLocation, currentTime);
        
        return gpsScore >= networkScore ? lastGpsLocation : lastNetworkLocation;
    }
    
    /**
     * 计算位置数据的综合评分
     * @param location 位置数据
     * @param currentTime 当前时间
     * @return 评分（越高越好）
     */
    private float calculateLocationScore(Location location, long currentTime) {
        if (location == null) return 0;
        
        float score = 100; // 基础分数
        
        // 精度评分（精度越高分数越高）
        float accuracy = location.getAccuracy();
        if (accuracy <= 5) {
            score += 50; // 高精度加分
        } else if (accuracy <= 20) {
            score += 30; // 中等精度加分
        } else if (accuracy <= 50) {
            score += 10; // 低精度少量加分
        } else {
            score -= (accuracy - 50) * 0.5f; // 精度太差扣分
        }
        
        // 时效性评分（越新鲜分数越高）
        long age = currentTime - location.getTime();
        if (age <= 1000) {
            score += 30; // 1秒内的数据加分
        } else if (age <= 5000) {
            score += 20; // 5秒内的数据加分
        } else if (age <= 15000) {
            score += 10; // 15秒内的数据少量加分
        } else {
            score -= (age - 15000) * 0.001f; // 超过15秒的数据扣分
        }
        
        // GPS提供者加分
        if (PROVIDER_GPS.equals(location.getProvider())) {
            score += 20;
        }
        
        // 如果有速度信息且合理，加分
        if (location.hasSpeed() && location.getSpeed() >= 0 && location.getSpeed() <= 83.33f) {
            score += 15;
        }
        
        return Math.max(0, score);
    }
    
    /**
     * 位置更新监听器接口
     */
    public interface OnLocationUpdateListener {
        void onLocationUpdated(Location location);
    }
    
    /**
     * GPS状态变化监听器接口
     */
    public interface OnGpsStatusChangeListener {
        void onGpsStatusChanged(int satellitesInFix, int satelliteCount, boolean isConnected);
        void onProviderStatusChanged(boolean isGpsEnabled, boolean isNetworkEnabled);
    }
}
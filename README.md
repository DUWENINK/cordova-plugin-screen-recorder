## 安卓屏幕录制插件

Android 屏幕录制（屏幕捕捉）的 Cordova 插件。此插件可以将屏幕录制保存为视频到设备的相册中，当用户首次开始录制时，它会请求必要的权限，并在屏幕录制期间创建一个活动通知。

## 必备条件
[Apache Cordova](https://cordova.apache.org/)<br>

在 `Cordova Android 12.0.0`下运行正常

## Installation
在`Cordova `根目录运行以下命令:
```
cordova plugin add https://github.com/DUWENINK/cordova-plugin-screen-recorder.git
```

## Usage
首先，将以下偏好设置添加到项目的 `config.xml` 文件中。
```xml
<preference name="AndroidXEnabled" value="true" />
```

现在你可以继续编写应用的代码，使用以下代码开始录制：
```js
ScreenRecord.startRecord(opts, fileName, function success(), function error());
```
`opts` 是一个具有以下属性的 JSON 对象：

- `recordAudio`：是否录制音频，默认为 `false`。
- `bitRate`：视频比特率，默认为 `6000000`。
- `title`：通知标题，默认为 `Screen Recording`。
- `text`：通知文本，默认为 `Screen recording active...`。
- `isHide`:是否推出到后台,默认为`true`。


要停止录制，调用以下方法：
```js
ScreenRecord.stopRecord(function success(), function error());
```
当录制停止后，视频会被保存到内存中，然后你可以从相册中播放视频。



## License
[996.ICU](https://github.com/996icu/996.ICU): 工作 996，生病 ICU。
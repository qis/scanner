# Android Project
Reset repository.

```cmd
git clean -d -X -f
```

Create screenshot.

```cmd
adb shell screencap -p /sdcard/screenshot.png
adb pull /sdcard/screenshot.png
adb shell rm /sdcard/screenshot.png
bash -c "mogrify -filter Lanczos -resize 480x screenshot.png"
bash -c "pngcrush -brute -reduce -rem allb -ow screenshot.png"
```

Create screen recording (stop with CTRL+C).

```cmd
adb shell screenrecord /sdcard/screenrecord.mp4
adb pull /sdcard/screenrecord.mp4
bash -c "ffmpeg -i screenrecord.mp4 -vf 'scale=480:-1:flags=lanczos' -c:v ffv1 screenrecord.mkv"
bash -c "ffmpeg -i screenrecord.mkv -vf 'palettegen' screenrecord.png"
bash -c "ffmpeg -i screenrecord.mkv -i screenrecord.png -r 60 -filter_complex 'fps=60,paletteuse' screenrecord.gif"
del screenrecord.mkv screenrecord.png screenrecord.mp4
adb shell rm /sdcard/screenrecord.mp4
```

"""Run the actual UI on one authorized phone with the existing isolated HTTP fixture."""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid
import admin_batch_fixture as fixture

main = fixture.root.parents[2]
adb = Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe'
devices = [line.split()[0] for line in subprocess.check_output([str(adb), 'devices'], text=True).splitlines()
           if line.endswith('\tdevice') and not line.startswith('emulator-')]
requested = os.environ.get('ATHAR_PREMIUM_DEVICE')
assert requested in devices if requested else len(devices) == 1, 'Select exactly one authorized physical device.'
serial = requested or devices[0]
evidence = main / 'artifacts/premium-ui/device' / uuid.uuid4().hex[:8]
evidence.mkdir(parents=True)

def device(*args, timeout=30, binary=False):
    result = subprocess.run([str(adb), '-s', serial, *args], capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError('Selected-device command failed: ' + result.stderr.decode('utf-8', errors='replace')[-500:])
    return result.stdout if binary else result.stdout.decode('utf-8', errors='replace')

def private_file(name):
    return device('exec-out', 'run-as', 'org.athar.app.debug', 'cat', 'files/' + name, binary=True)

assert device('shell', 'getprop', 'sys.boot_completed').strip() == '1'
for apk in [main/'android/app/build/outputs/apk/debug/app-debug.apk',
            main/'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
    assert apk.is_file()
    assert 'Success' in device('install', '--no-streaming', '-r', str(apk), timeout=90)
print('Updated debug and test APKs in place on the selected phone; no data clear.', flush=True)
for line in device('reverse', '--list').splitlines():
    parts = line.split()
    if len(parts) >= 3 and parts[1] == 'tcp:8004':
        assert parts[2] == 'tcp:8004', 'Existing reverse mapping belongs to another target.'
device('reverse', 'tcp:8004', 'tcp:8004')

outcomes = []
captures = []
try:
    for language, method in [('ar', 'realScreensKeepNavigationAndContentInBothThemes'),
                             ('en', 'realScreensKeepNavigationAndContentInBothThemes'),
                             ('ar', 'actualMedia3ControlsSurviveNavigationAndBackground')]:
        run_id = uuid.uuid4().hex[:8]
        prefix = f'premium-{language}-{run_id}'
        command = [str(adb), '-s', serial, 'shell', 'am', 'instrument', '-w', '-r',
                   '-e', 'class', 'org.athar.app.PremiumUiJourneyTest#' + method,
                   '-e', 'nativeApiSmoke', 'true', '-e', 'nativeFixtureHost', '127.0.0.1',
                   '-e', 'reviewLanguage', language, '-e', 'reviewRun', run_id,
                   '-e', 'liveAudioSmoke', 'true', 'org.athar.app.debug.test/org.athar.app.AtharTestRunner']
        print(f'Running actual {language} {method}.', flush=True)
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        recording = None
        remote_video = f'/sdcard/athar-premium-{run_id}.mp4'
        if language == 'ar' and method.startswith('realScreens'):
            deadline = time.monotonic() + 90
            while process.poll() is None and time.monotonic() < deadline:
                ready = subprocess.run([str(adb), '-s', serial, 'exec-out', 'run-as', 'org.athar.app.debug', 'cat',
                                        'files/' + prefix + '-ready.txt'], capture_output=True, timeout=10)
                if ready.returncode == 0 and ready.stdout == b'ready':
                    # Begin only after the test-owned Home is visible, avoiding unrelated account UI.
                    recording = subprocess.Popen([str(adb), '-s', serial, 'shell', 'screenrecord', '--bit-rate', '1500000',
                                                  '--time-limit', '60', remote_video], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                    break
                time.sleep(.5)
        stdout, stderr = process.communicate(timeout=420)
        output = (stdout + stderr).decode('utf-8', errors='replace')
        (evidence / f'{language}-{method}.log').write_text(output, encoding='utf-8')
        # Extract only this run's captures, including useful partial evidence if the assertion failed.
        try:
            names = private_file(prefix + '-manifest.txt').decode().splitlines()
            for name in names:
                assert re.fullmatch(re.escape(prefix) + r'-[a-z-]+\.png', name)
                raw = private_file(name)
                assert raw.startswith(b'\x89PNG\r\n\x1a\n')
                (evidence / name).write_bytes(raw)
                captures.append(name)
        except RuntimeError:
            pass
        if recording:
            recording.communicate(timeout=70)
            video = device('exec-out', 'cat', remote_video, timeout=30, binary=True)
            if len(video) > 1024:
                (evidence / 'premium-ar-navigation.mp4').write_bytes(video)
                device('shell', 'rm', remote_video)
        passed = 'OK (1 test)' in output and 'FAILURES!!!' not in output and 'INSTRUMENTATION_FAILED' not in output
        outcomes.append({'language': language, 'test': method, 'passed': passed})
        print(('PASS' if passed else 'FAIL') + f': {language} {method}; {len(captures)} captures so far.', flush=True)
        if not passed:
            raise RuntimeError(output[-3500:])
finally:
    (evidence / 'results.json').write_text(json.dumps({'fixture_data': True, 'device': 'physical Android', 'tests': outcomes, 'captures': captures}, indent=2))
    print('Evidence: ' + str(evidence), flush=True)
print('Actual fixture-backed UI and live Media3 review journeys passed.', flush=True)

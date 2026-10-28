"""Owned fixture + one physical Samsung session, preserving installed/private app data."""
import json
import hashlib
import re
import os
from pathlib import Path
import subprocess
import admin_batch_fixture as fixture

adb=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
available=[line.split()[0] for line in subprocess.check_output([str(adb),'devices'],text=True).splitlines() if line.endswith('\tdevice') and not line.startswith('emulator-')]
requested=os.environ.get('ATHAR_PREMIUM_DEVICE')
assert requested in available if requested else len(available)==1, 'Select exactly one authorized physical device.'
serial=requested or available[0]
def device(*args,timeout=240):
    result=subprocess.run([str(adb),'-s',serial,*args],capture_output=True,text=True,encoding="utf-8",errors="replace",timeout=timeout)
    if result.returncode:raise RuntimeError('Selected-device operation failed: '+result.stderr[-500:])
    return result.stdout

assert 'device' in subprocess.check_output([str(adb),'-s',serial,'get-state'],text=True,encoding='utf-8')
assert device('shell','getprop','ro.build.version.sdk').strip()=='27'
assert device('shell','getprop','ro.product.cpu.abi').strip()=='armeabi-v7a'
main=fixture.root.parents[2]
assert (main/'scripts/test-admin-recovery.ps1').is_file()
artifacts=[main/'android/app/build/outputs/apk/debug/app-debug.apk',main/'android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']
for artifact,package in zip(artifacts,['org.athar.app.debug','org.athar.app.debug.test']):
    assert artifact.is_file()
    actual=device('shell','pm','path',package).strip().removeprefix('package:')
    if re.fullmatch(r'/data/app/[A-Za-z0-9/_.=~+-]+/base\.apk',actual):
        digest=device('shell','sha256sum',actual).split()[0]
        if digest==hashlib.sha256(artifact.read_bytes()).hexdigest():
            print('Reused exact installed '+artifact.name+' after on-device SHA-256 comparison.',flush=True)
            continue
    output=device('install','--no-streaming','-r',str(artifact))
    assert 'Success' in output
    print('Installed reviewed '+artifact.name+' with update-in-place; no uninstall/data clear.',flush=True)
existing=device('reverse','--list')
for line in existing.splitlines():
    parts=line.split()
    if len(parts)>=3 and parts[1]=='tcp:8004':assert parts[2]=='tcp:8004'
device('reverse','tcp:8004','tcp:8004')
# A new random immutable fixture ID cannot collide with retained prior QA-owned rows.
account=fixture.service("$id=random_int(500000000,1500000000);$email='home_'.$id.'@example.test';$db->prepare(\"INSERT INTO users(id,email,username,display_name,password_hash,email_verified_at) VALUES (?,?,?,?,?,UTC_TIMESTAMP())\")->execute([$id,$email,'home'.$id,'Home fixture',password_hash('HomeFixture123!',PASSWORD_DEFAULT)]);$db->prepare(\"INSERT INTO account_daily_stats(user_id,activity_date,quran_sessions,lesson_completions,adhkar_completions) VALUES (?,UTC_DATE(),7,2,3)\")->execute([$id]);echo json_encode(['id'=>$id,'email'=>$email]);")
classes='org.athar.app.ImmersiveHeroUiTest,org.athar.app.HomeNavigationJourneyTest'
output=device('shell','am','instrument','-w','-r','-e','class',classes,'-e','nativeApiSmoke','true','-e','nativeFixtureHost','127.0.0.1','-e','homeFixtureEmail',account['email'],'org.athar.app.debug.test/org.athar.app.AtharTestRunner',timeout=420)
(fixture.root/'storage/engineering/native-home-instrumentation.log').write_text(output,encoding='utf-8')
if 'FAILURES!!!' in output or 'INSTRUMENTATION_FAILED' in output or 'OK (' not in output:
    raise RuntimeError('Native Home/hero acceptance failed; see owned instrumentation log.\n'+output[-3000:])
captures=['immersive-hero-ar-false-1.0.png','immersive-hero-en-true-1.5.png','immersive-hero-ar-false-2.0.png','home-native-ar.png','home-native-en.png','home-native-en-landscape.png']
for name in captures:
    result=subprocess.run([str(adb),'-s',serial,'exec-out','run-as','org.athar.app.debug','cat','files/'+name],capture_output=True,timeout=30)
    assert result.returncode==0 and result.stdout.startswith(b'\x89PNG\r\n\x1a\n')
    (fixture.root/'storage/engineering'/name).write_bytes(result.stdout)
print('PASS: Samsung API27 ARM32 actual hero/Home acceptance; see instrumentation test count and captures. No uninstall/reset/private row clearing/global settings change.',flush=True)

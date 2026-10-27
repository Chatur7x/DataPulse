"""Check the merged APK permission surface, ignoring comments."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

path = Path(sys.argv[1]) if len(sys.argv) > 1 else next(Path('app/build/intermediates/merged_manifests/debug').rglob('AndroidManifest.xml'))
root = ET.parse(path).getroot()
android = '{http://schemas.android.com/apk/res/android}'
permissions = {p.get(android + 'name') for p in root.findall('uses-permission')}
assert 'android.permission.INTERNET' in permissions, 'Missing explicit speed-test network permission'
assert 'android.permission.PACKAGE_USAGE_STATS' in permissions, 'Missing Usage Access declaration'
assert 'android.permission.READ_PHONE_STATE' in permissions, 'Missing optional SIM detection permission'
assert not permissions.intersection({'android.permission.READ_SMS', 'android.permission.RECEIVE_SMS', 'android.permission.READ_CALL_LOG', 'android.permission.READ_CONTACTS', 'android.permission.READ_PHONE_NUMBERS', 'android.permission.READ_PRIVILEGED_PHONE_STATE'}), 'Unexpected sensitive access beyond SIM detection'
app = root.find('application')
assert app is not None and app.get(android + 'allowBackup') == 'false'
assert app.get(android + 'usesCleartextTraffic') == 'false'
print('PASS: HTTPS speed-test permission; Usage Access declared; backup and cleartext disabled')

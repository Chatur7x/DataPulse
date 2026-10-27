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
app = root.find('application')
assert app is not None and app.get(android + 'allowBackup') == 'false'
assert app.get(android + 'usesCleartextTraffic') == 'false'
print('PASS: HTTPS speed-test permission; Usage Access declared; backup and cleartext disabled')

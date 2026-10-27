"""Deterministic UI fallback, using verified Compose labels on a dedicated emulator.
Run with a Python environment containing uiautomator2. Does not grant permissions,
send files, or delete records. Expects a fresh app with no plan.
"""
import json
import sys
import time
from pathlib import Path
import uiautomator2 as u2

device = u2.connect(sys.argv[1] if len(sys.argv) > 1 else 'emulator-5554')
out = Path(sys.argv[2] if len(sys.argv) > 2 else 'work/device-evidence')
out.mkdir(parents=True, exist_ok=True)
results = []

def check(label, condition):
    assert condition, label
    results.append({'check': label, 'passed': True})
    print('PASS:', label, flush=True)

def reveal(text):
    for _ in range(8):
        if device(text=text).exists:
            bounds = device(text=text).info['bounds']
            if bounds['bottom'] < device.window_size()[1] - 170:
                return
        device.swipe_ext('up', scale=0.55)
    assert device(text=text).exists, text

def top():
    for _ in range(5): device.swipe_ext('down', scale=0.7)
    time.sleep(0.8)  # Let the verified Compose overscroll stretch settle before capture.

def enter(label, value):
    reveal(label)
    field = device.xpath(f'//android.widget.EditText[.//*[@text="{label}"]]')
    field.set_text(value)
    if device(className='android.inputmethodservice.KeyboardView').exists: device.press('back')
    # set_text can summon the system IME. Close it only when the IME is visible.
    if device.shell('dumpsys input_method').output.find('mInputShown=true') >= 0: device.press('back')

device.app_start('com.datapulse.app')
assert device(text='DataPulse').wait(timeout=15)
top()
if device(text='Set up my plan').exists:
    check('Permission-denied explainer', device(text='Connect your usage').exists)
    device.screenshot().save(out/'onboarding.png')
    device(text='Set up my plan').click()
check('Plan form opens', device(text='A plan that fits you.').wait(timeout=10))
reveal('Save my plan')
device(text='Save my plan').click()
check('Invalid input stays on form', device(textStartsWith='Check all fields.').wait(timeout=5))
device.screenshot().save(out/'validation.png')
top()
enter('Carrier or connection name','Test carrier')
enter('Data per day (GB)','1.5')
enter('Price paid (₹)','299')
reveal('Save my plan')
device(text='Save my plan').click()
check('Plan saves and returns to overview', device(text='Your data, in balance.').wait(timeout=10))
for tab, title in [('Trends','Find your rhythm.'), ('Apps','Where it goes.'), ('My plan','Your connection'), ('Settings','Private by design.')]:
    device(text=tab).click()
    top()
    check(f'{tab} navigation', device(text=title).wait(timeout=5))
    device.screenshot().save(out/(tab.lower().replace(' ','-')+'.png'))
reveal('Quiet hours (23:00–07:00)')
switch = device.xpath('//*[@text="Quiet hours (23:00–07:00)"]/following-sibling::*[@checkable="true"]')
assert switch.exists, 'Quiet-hours switch is reachable'
old = switch.get().attrib.get('checked')
switch.click()
device(text='Apps').click()
device(text='Settings').click()
reveal('Quiet hours (23:00–07:00)')
check('Quiet hours persists', switch.get().attrib.get('checked') != old)
reveal('Delete all local records')
device(text='Delete all local records').click()
check('Delete confirmation opens',device(text='Delete local records?').wait(timeout=5))
device(text='Cancel').click()
device(text='My plan').click()
top()
check('Cancel deletion preserves plan',device(text='Test carrier').exists)
(out/'results.json').write_text(json.dumps(results,indent=2),encoding='utf-8')
print(json.dumps(results,indent=2))

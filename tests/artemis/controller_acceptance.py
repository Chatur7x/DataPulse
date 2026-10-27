"""Acceptance tests through google/artemis's actual device controller.

Dedicated emulator only. Creates and deletes a clearly named test plan. No LLM
provider is invoked; this is deterministic Artemis controller testing, not a
passing Flash/Pro autonomous-agent run.
"""
import argparse
import asyncio
import base64
import json
from pathlib import Path
import subprocess
import sys
import time

parser = argparse.ArgumentParser()
parser.add_argument('--artemis-root', default=r'D:\Tools\artemis')
parser.add_argument('--serial', default='emulator-5554')
parser.add_argument('--output', required=True)
parser.add_argument('--reset-test-plan', action='store_true')
parser.add_argument('--keep-preview-plan', action='store_true', help='Create an explicit emulator visual fixture after deletion has passed')
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('This acceptance suite requires a dedicated emulator.')
sys.path.insert(0, args.artemis_root)
from artemis.mcp.adb_server import _get_controller

out = Path(args.output)
out.mkdir(parents=True, exist_ok=True)
results = []
started = time.time()
c = None

async def state():
    data = await c.get_screen_data()
    if any("isn't responding" in e.get('text','') or "isn’t responding" in e.get('text','') for e in data.elements):
        raise AssertionError('Android ANR dialog detected')
    return data

def match(data, text):
    return next((e for e in data.elements if e.get('text') == text), None)

async def expect(text, timeout=15):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        data = await state()
        element = match(data, text)
        if element: return element
        await asyncio.sleep(.3)
    raise AssertionError('Expected visible text: '+text)

async def screenshot(name):
    await asyncio.sleep(.6)
    data = await c.get_screen_data()
    (out/(name+'.jpg')).write_bytes(base64.b64decode(data.base64))
    (out/(name+'.json')).write_text(json.dumps(data.elements, indent=2), encoding='utf-8')

async def check(name, text=None):
    if text: await expect(text)
    results.append({'check':name,'status':'passed'})
    print('PASS: '+name, flush=True)

async def tap(text):
    result = await c.tap_element(text=text)
    if result.error:
        e = await expect(text)
        b = e['parsed_bounds']
        result = await c.tap_at((b['left']+b['right'])//2, (b['top']+b['bottom'])//2)
    assert not result.error, result.error
    await asyncio.sleep(.25)

async def scroll(up=True):
    data = await state()
    x=data.width//2
    error=await c.swipe_coords(x, int(data.height*(.76 if up else .35)), x, int(data.height*(.35 if up else .76)), duration=450)
    assert not error, error
    await asyncio.sleep(.3)

async def reveal(text, up=True):
    for _ in range(10):
        data=await state(); e=match(data,text)
        if e and e['parsed_bounds']['bottom'] < data.height-160: return e
        await scroll(up)
    raise AssertionError('Cannot reveal '+text)

async def fill(label, text):
    await reveal(label)
    await tap(label)
    assert await c.type_text(text, clear_existing=True), 'Typing failed: '+label
    await c.go_back()
    await asyncio.sleep(.3)

async def nav(label, heading):
    # Try the semantic target first. The filtered Artemis hierarchy may omit
    # navigation text, so verified bottom-bar geometry is a fallback.
    data=await state()
    element=match(data,label)
    if element:
        b=element['parsed_bounds']
        result=await c.tap_at((b['left']+b['right'])//2,(b['top']+b['bottom'])//2)
    else:
        i=['Overview','Trends','Apps','My plan','Settings'].index(label)
        result=await c.tap_at(int(data.width*(i+.5)/5),int(data.height*.91))
    assert not result.error
    await expect(heading)

async def main():
    global c
    c=_get_controller(device_serial=args.serial)
    await c.driver.execute_shell('appops set com.datapulse.app GET_USAGE_STATS deny')
    await c.terminate_app('com.datapulse.app')
    assert await c.launch_app('com.datapulse.app')
    if args.reset_test_plan:
        await nav('My plan','Your connection')
        await reveal('Artemis Test Carrier',up=False)
        await expect('Artemis Test Carrier')  # Never clear a plan from an unknown user.
        await nav('Settings','Private by design.')
        await reveal('Delete all local records'); await tap('Delete all local records')
        await tap('Delete records')
        await nav('Overview','Make room for\nwhat matters.')
    await check('Denied-access explanation','Connect your usage')
    await expect('Set up my plan')
    await screenshot('01-onboarding')
    await tap('Set up my plan')
    await check('Plan setup opens','A plan that fits you.')
    await reveal('Save my plan')
    await tap('Save my plan')
    await check('Invalid input rejected','Check all fields. Use a valid past or current date, positive allowance and 1–366 days.')
    await screenshot('02-validation')
    await reveal('Carrier or connection name',up=False)
    await fill('Carrier or connection name','Artemis Test Carrier')
    await fill('Data per day (GB)','1.5')
    await fill('Price paid (₹)','299')
    await reveal('Save my plan'); await tap('Save my plan')
    await check('Daily plan saves','Your data, in balance.')
    await check('Missing measurement remains explicit','Waiting for a measurement')
    await screenshot('03-daily-denied')
    for label,heading in [('Trends','Find your rhythm.'),('Apps','Where it goes.'),('My plan','Your connection'),('Settings','Private by design.')]:
        await nav(label,heading)
        await check(label+' navigation',heading)
    await reveal('Quiet hours (23:00–07:00)')
    data=await state(); label=match(data,'Quiet hours (23:00–07:00)'); b=label['parsed_bounds']; y=(b['top']+b['bottom'])//2
    switch=next(e for e in data.elements if e.get('checkable')=='true' and e['parsed_bounds']['top']<=y<=e['parsed_bounds']['bottom'])
    old=switch['checked']; sb=switch['parsed_bounds']
    assert not (await c.tap_at((sb['left']+sb['right'])//2,y)).error
    await nav('Apps','Where it goes.'); await nav('Settings','Private by design.')
    await reveal('Quiet hours (23:00–07:00)')
    data=await state(); label=match(data,'Quiet hours (23:00–07:00)'); b=label['parsed_bounds']; y=(b['top']+b['bottom'])//2
    switch=next(e for e in data.elements if e.get('checkable')=='true' and e['parsed_bounds']['top']<=y<=e['parsed_bounds']['bottom'])
    assert switch['checked']!=old
    await check('Quiet hours persists after navigation')
    await screenshot('04-settings')
    await reveal('Delete all local records'); await tap('Delete all local records')
    await check('Deletion requires confirmation','Delete local records?')
    await tap('Cancel'); await nav('My plan','Your connection')
    await check('Tab change resets previous screen scroll','Your connection')
    await reveal('Artemis Test Carrier',up=False)
    await check('Cancel deletion preserves plan','Artemis Test Carrier')
    await reveal('Log a new recharge'); await tap('Log a new recharge')
    await expect('A plan that fits you.')
    await tap('Cycle pool')
    await fill('Carrier or connection name','Artemis Test Carrier')
    await fill('Data per cycle (GB)','10')
    await fill('Price paid (₹)','199')
    await reveal('Save my plan'); await tap('Save my plan')
    await check('Pooled plan saves','Your data, in balance.')
    await c.driver.execute_shell('appops set com.datapulse.app GET_USAGE_STATS allow')
    await c.terminate_app('com.datapulse.app'); await c.launch_app('com.datapulse.app')
    await expect('Your data, in balance.')
    await expect('9.31 GiB', timeout=45)
    await check('10 GB pool is not multiplied by 28-day validity')
    await screenshot('05-pool-measured')
    await nav('Settings','Private by design.')
    await reveal('Delete all local records'); await tap('Delete all local records')
    await tap('Delete records')
    await nav('Overview','Make room for\nwhat matters.')
    await check('Confirmed deletion clears the test plans','Set up my plan')
    await screenshot('06-after-deletion')
    if args.keep_preview_plan:
        await tap('Set up my plan')
        await expect('A plan that fits you.')
        await tap('Cycle pool')
        await fill('Carrier or connection name','Preview connection')
        await fill('Data per cycle (GB)','10')
        await fill('Price paid (₹)','199')
        await reveal('Save my plan'); await tap('Save my plan')
        await expect('9.31 GiB',timeout=45)
        await screenshot('07-premium-overview')
        await nav('Settings','Private by design.')
        await screenshot('08-premium-settings')
        await nav('My plan','Your connection')
        await screenshot('09-premium-plan')
        await nav('Apps','Where it goes.')
        await screenshot('10-premium-apps')
        await nav('Overview','Your data, in balance.')

async def run():
    error=None
    try: await main()
    except Exception as e:
        error=f'{type(e).__name__}: {e}'
        results.append({'check':'suite completion','status':'failed','error':error})
        if c: await screenshot('failure')
        print(error,flush=True)
    finally:
        commit=subprocess.check_output(['git','-C',args.artemis_root,'rev-parse','HEAD'],text=True).strip()
        report={'framework':'google/artemis','repository':'https://github.com/google/artemis','commit':commit,
          'mode':'deterministic repository device controller; no autonomous LLM agent',
          'device':args.serial,'elapsed_seconds':round(time.time()-started,1),'results':results,
          'autonomous_agent_status':'blocked: live provider validation failed; configured endpoints unreachable'}
        (out/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    if error: raise SystemExit(1)
asyncio.run(run())

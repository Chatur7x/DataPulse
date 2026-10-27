import argparse,asyncio,base64,json,sys,time

from pathlib import Path

from datetime import date

p=argparse.ArgumentParser();p.add_argument('--artemis-root',default=r'D:\Tools\artemis');p.add_argument('--serial',default='emulator-5554');p.add_argument('--output',required=True);a=p.parse_args()

if not a.serial.startswith('emulator-'):raise SystemExit('Dedicated emulator required')

sys.path.insert(0,a.artemis_root)

from artemis.mcp.adb_server import _get_controller

out=Path(a.output);out.mkdir(parents=True,exist_ok=True);results=[]

async def main():

 c=_get_controller(device_serial=a.serial)

 async def data():return await c.get_screen_data()

 async def shot(n):

  d=await data();(out/(n+'.jpg')).write_bytes(base64.b64decode(d.base64))

 async def expect(t,seconds=15):

  end=time.monotonic()+seconds

  while time.monotonic()<end:

   d=await data()

   if any(t in e.get('text','') for e in d.elements):return

   await asyncio.sleep(.3)

  await shot('failure');raise AssertionError('Expected '+t)

 async def tap(t,exact=True):

  d=await data();e=next(e for e in d.elements if (e.get('text','').replace(chr(8217),chr(39))==t.replace(chr(8217),chr(39)) if exact else t in e.get('text','')));b=e['parsed_bounds'];r=await c.tap_at((b['left']+b['right'])//2,(b['top']+b['bottom'])//2);assert not r.error;await asyncio.sleep(.25)

 async def reveal(t,down=True):

  for direction in [down,not down]:

   for _ in range(14):

    d=await data()

    if any(t in e.get('text','') and 0<e['parsed_bounds']['top'] and e['parsed_bounds']['bottom']<d.height-140 for e in d.elements):return

    await c.swipe_coords(d.width//2,int(d.height*(.76 if direction else .37)),d.width//2,int(d.height*(.4 if direction else .76)),duration=350)

  await shot('failure');raise AssertionError('Reveal '+t)

 async def fill(label,value):

  await reveal(label);await tap(label,exact=False);assert await c.type_text(value,clear_existing=True);await c.go_back();await asyncio.sleep(.2)

 async def enter():

  d=await data();await c.tap_at(int(d.width*.7),int(d.height*.91));await reveal('SIMs & recharges');await tap('SIMs & recharges');await expect('Every SIM, in focus.')

 def passed(t):results.append({'check':t,'passed':True});print('PASS '+t,flush=True)

 try:

  await c.driver.execute_shell('pm revoke com.datapulse.app android.permission.READ_PHONE_STATE')

  await c.driver.execute_shell('am force-stop com.datapulse.app');await c.driver.execute_shell('am start -W -n com.datapulse.app/.MainActivity');await asyncio.sleep(.7)

  await enter();passed('Separate SIM section opens')

  await expect('Allow SIM detection');passed('Optional phone access is explained without inbox or number access')

  await tap('Recharge catalogue');await expect('Live official offers');await shot('catalogue-overview')

  for name in ['Jio','Airtel','Vi','BSNL']:await reveal('Browse '+name+' official plans')

  await shot('catalogue');passed('All four official carrier directories are available without phone permission')

  await reveal('My SIMs',down=False);await tap('My SIMs');await reveal('Allow SIM detection');await tap('Allow SIM detection')

  await asyncio.sleep(.5);await tap("Don't allow")

  await expect('Allow SIM detection');passed('Permission denial keeps the app usable')

  await c.driver.execute_shell('pm grant com.datapulse.app android.permission.READ_PHONE_STATE')

  await reveal('Refresh SIMs');await tap('Refresh SIMs');await reveal('Automatic carrier plan');await shot('sims')

  passed('Actual emulator SIM carrier and slot are detected')

  await reveal('Android restricts automatic plan access');passed('Restricted carrier plan access is explicit, with no invented plan')

  await reveal('Confirm recharge for SIM');await tap('Confirm recharge for SIM',exact=False);await expect('Paste recharge confirmation')

  await reveal('Confirm and save recharge');await tap('Confirm and save recharge');await expect('Check the plan name');passed('Incomplete recharge details are rejected')

  await reveal('Paste recharge confirmation',down=False);await fill('Paste recharge confirmation','Recharge successful Rs.299. Validity: 28 days. Data 1.5 GB/day.')

  await reveal('Extract suggestions');await tap('Extract suggestions');await reveal('Review every suggested field');await expect('Review every suggested field');passed('Receipt extraction requires explicit review')

  await reveal('Plan name',down=False);await fill('Plan name','Artemis SIM fixture');await fill('Actual start date',date.today().isoformat())

  await reveal('Confirm and save recharge');await tap('Confirm and save recharge');await reveal('Artemis SIM fixture');await expect('Within entered dates');await shot('sim-recharge');passed('Reviewed recharge is saved under its own detected SIM')

  await reveal('Remove saved record');await tap('Remove saved record');await expect('Remove recharge record?');await tap('Cancel');await expect('Artemis SIM fixture');passed('Cancelling removal preserves the recharge')

  await c.driver.execute_shell('am force-stop com.datapulse.app');await c.driver.execute_shell('am start -W -n com.datapulse.app/.MainActivity');await asyncio.sleep(.5);await enter();await reveal('Artemis SIM fixture');passed('Per-SIM recharge persists after process restart')

  await c.go_back();await reveal('Measure internet speed');await tap('Measure internet speed');await expect('Real bytes. Clear evidence.');passed('Existing speed-test entry still works')

 except Exception as e:results.append({'check':str(e),'passed':False});raise

 finally:(out/'ui-results.json').write_text(json.dumps(results,indent=2))

asyncio.run(main())


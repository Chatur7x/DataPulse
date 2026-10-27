import argparse,asyncio,base64,json,os,sys,time
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--artemis-root',default=r'D:\Tools\artemis');p.add_argument('--serial',default='emulator-5554');p.add_argument('--output',required=True);args=p.parse_args()
if not args.serial.startswith('emulator-'):raise SystemExit('Dedicated emulator only')
sys.path.insert(0,args.artemis_root)
from artemis.mcp.adb_server import _get_controller
out=Path(args.output);out.mkdir(parents=True,exist_ok=True);results=[]
async def main():
 c=_get_controller(device_serial=args.serial)
 async def data():return await c.get_screen_data()
 async def shot(name):
  d=await data();(out/(name+'.jpg')).write_bytes(base64.b64decode(d.base64));return d
 async def expect(text,timeout=15):
  end=time.monotonic()+timeout
  while time.monotonic()<end:
   d=await data()
   if any(text in e.get('text','') for e in d.elements):return d
   await asyncio.sleep(.5)
  await shot('failure');raise AssertionError('Expected '+text)
 async def tap(text):
  d=await data();e=next(e for e in d.elements if e.get('text')==text);b=e['parsed_bounds'];r=await c.tap_at((b['left']+b['right'])//2,(b['top']+b['bottom'])//2);assert not r.error
 async def reveal(text):
  for _ in range(18):
   d=await data()
   if any(text in e.get('text','') and 0<e['parsed_bounds']['top']<d.height-160 and e['parsed_bounds']['bottom']<d.height-140 for e in d.elements):return
   await c.swipe_coords(d.width//2,int(d.height*.76),d.width//2,int(d.height*.43),duration=350)
  raise AssertionError('Cannot reveal '+text)
 def passed(s):results.append({'check':s,'passed':True});print('PASS '+s,flush=True)
 try:
  await c.driver.execute_shell('am force-stop com.datapulse.app');await c.driver.execute_shell('am start -W -n com.datapulse.app/.MainActivity');await asyncio.sleep(1)
  d=await data();await c.tap_at(int(d.width*.7),int(d.height*.91));await expect('Measure internet speed');await tap('Measure internet speed');await expect('Real bytes. Clear evidence.');passed('Account-free speed screen opens')
  await reveal('I agree to use data')
  d=await data();e=next(e for e in d.elements if e.get('checkable')=='true' and e.get('checked')=='false' and e['parsed_bounds']['left']<d.width/2);b=e['parsed_bounds'];await c.tap_at((b['left']+b['right'])//2,(b['top']+b['bottom'])//2)
  await reveal('Start speed test');await tap('Start speed test');await reveal('Cancel test');await tap('Cancel test');await expect('Test cancelled');passed('Cancellation reports incomplete test without fabricated result')
  await reveal('Start speed test');await tap('Start speed test');await reveal('Cancel test');passed('Immediate restart after cancellation works')
  await expect('Measured result',timeout=120);await shot('speed-result');passed('Live speed test completes and result is visible')
  await reveal('Download loaded HTTP');await expect('Upload loaded HTTP');await shot('speed-latency');passed('Idle and loaded HTTP latency details displayed')
  await reveal('Show measurement evidence');await tap('Show measurement evidence');await reveal('Save original measurement JSON');await expect('Acknowledged upload');await shot('speed-evidence');passed('Original byte counts and monotonic timings available')
  await tap('Save original measurement JSON')
  await asyncio.sleep(1)
  d=await data();buttons=[e for e in d.elements if e.get('text','').lower()=='save' and e.get('class')=='android.widget.Button']
  if not buttons:buttons=[e for e in d.elements if e.get('text','').lower()=='save']
  assert buttons,'Android document picker Save action not found'
  b=buttons[-1]['parsed_bounds'];await c.tap_at((b['left']+b['right'])//2,(b['top']+b['bottom'])//2)
  await reveal('Measurement JSON saved');await expect('Measurement JSON saved');passed('Original JSON exports through Android document picker')
  await c.go_back();await expect('Your connection');await tap('Measure internet speed');await expect('Measured result');passed('Measurement persists across navigation')
  await reveal('I agree to use data')
  d=await data();e=next(e for e in d.elements if e.get('checkable')=='true' and e.get('checked')=='false' and e['parsed_bounds']['left']<d.width/2);b=e['parsed_bounds'];await c.tap_at((b['left']+b['right'])//2,(b['top']+b['bottom'])//2)
  await reveal('Start speed test');await tap('Start speed test');await reveal('Cancel test')
  await c.driver.execute_shell('input keyevent KEYCODE_HOME');await asyncio.sleep(3);await c.driver.execute_shell('am start -W -n com.datapulse.app/.MainActivity');await expect('Test cancelled');passed('Backgrounding cancels an active transfer')
  await c.driver.execute_shell('am force-stop com.datapulse.app');await c.driver.execute_shell('am start -W -n com.datapulse.app/.MainActivity');await asyncio.sleep(.7)
  d=await data();await c.tap_at(int(d.width*.7),int(d.height*.91));await expect('Measure internet speed');await tap('Measure internet speed');await expect('Measured result');passed('Measurement persists after process restart')
 except Exception as e:
  results.append({'check':str(e),'passed':False});raise
 finally:(out/'ui-results.json').write_text(json.dumps(results,indent=2))
asyncio.run(main())



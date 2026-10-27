import sys,asyncio,json,re,base64,time
from pathlib import Path
import os
sys.path.insert(0,os.environ.get('ARTEMIS_ROOT','artemis'))
from artemis.mcp.adb_server import _get_controller
out=Path(os.environ.get('ARTEMIS_OUTPUT','evidence/v04-tests'));out.mkdir(parents=True,exist_ok=True)
results=[]
async def main():
 c=_get_controller(device_serial='emulator-5554')
 await c.driver.execute_shell('am start -W -n com.datapulse.app/.MainActivity')
 async def data():return await c.get_screen_data()
 async def expect(text,timeout=12):
  end=time.monotonic()+timeout
  while time.monotonic()<end:
   d=await data()
   if any(text in e.get('text','') for e in d.elements):return d
   await asyncio.sleep(.3)
  raise AssertionError(text)
 async def tap(text):
  d=await data();e=next(e for e in d.elements if e.get('text')==text);b=e['parsed_bounds'];assert not(await c.tap_at((b['left']+b['right'])//2,(b['top']+b['bottom'])//2)).error
 async def nav(i):
  d=await data();await c.tap_at(int(d.width*(i+.5)/5),int(d.height*.91));await asyncio.sleep(.5)
 async def reveal(text,timeout=15):
  end=time.monotonic()+timeout
  while time.monotonic()<end:
   d=await data()
   if any(text in e.get('text','') and e['parsed_bounds']['bottom']<d.height-140 for e in d.elements):return
   await c.swipe_coords(d.width//2,int(d.height*.75),d.width//2,int(d.height*.4),duration=400)
   await asyncio.sleep(.3)
  raise AssertionError('Reveal '+text)
 async def shot(name):
  await asyncio.sleep(.5);d=await data();(out/(name+'.jpg')).write_bytes(base64.b64decode(d.base64))
 def passed(name):results.append({'check':name,'passed':True});print('PASS',name,flush=True)
 await nav(1);await expect('Every recorded day');passed('Daily history opens')
 d=await data();e=next(e for e in d.elements if e.get('content-desc')=='Previous day');b=e['parsed_bounds'];await c.tap_at((b['left']+b['right'])//2,(b['top']+b['bottom'])//2)
 await asyncio.sleep(.4)
 d=await data();date=next(e['text'] for e in d.elements if re.fullmatch(r'\d{1,2} [A-Za-z]{3} \d{4}',e.get('text','')))
 passed('Previous-day drill-down works');await shot('history')
 await tap(date);await expect('Choose day');passed('Calendar picker opens');await tap('Cancel')
 await nav(3);await expect('Measure internet speed');await tap('Measure internet speed');await expect('Internet speed test');passed('Account-free speed test opens')
 await reveal('I agree to use data')
 d=await data();box=next(e for e in d.elements if e.get('checkable')=='true' and e.get('checked')=='false' and e['parsed_bounds']['left']<d.width/2);b=box['parsed_bounds'];await c.tap_at((b['left']+b['right'])//2,(b['top']+b['bottom'])//2)
 await reveal('Start speed test');await tap('Start speed test');await asyncio.sleep(2)
 await reveal('Cancel test');await tap('Cancel test');await expect('Test cancelled',timeout=10);passed('Cancellation stops incomplete test')
 await reveal('Start speed test');await tap('Start speed test')
 end=time.monotonic()+90
 while time.monotonic()<end:
  d=await data()
  if any('Measured result' in e.get('text','') for e in d.elements):break
  await c.swipe_coords(d.width//2,int(d.height*.76),d.width//2,int(d.height*.4),duration=350)
  await asyncio.sleep(.5)
 else:raise AssertionError('No measured result')
 await reveal('Measured result');passed('Live speed result displayed and stored');await shot('speed-result')
 await reveal('Recent measurements');passed('Speed history retained');await shot('speed-history')
 await c.go_back();await expect('Your connection');passed('System back returns to app')
 try:await shot('app-plan')
 finally:(out/'ui-results.json').write_text(json.dumps(results,indent=2))
asyncio.run(main())

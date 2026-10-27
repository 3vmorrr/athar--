const path=require('node:path');
const {chromium}=require(process.env.ATHAR_TEST_PLAYWRIGHT||'playwright');
const root=path.resolve(__dirname,'..');if(root!==path.resolve(process.env.ATHAR_RECOVERY_SANDBOX||''))throw Error('Copied fixture required');
let checks=0;function check(ok,label){if(!ok)throw Error(label);checks++;}
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});const context=await browser.newContext({viewport:{width:1440,height:900},reducedMotion:'reduce'});const page=await context.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto('http://127.0.0.1:8004/admin/login?lang=en');await page.locator('[name=email]').fill(process.env.ATHAR_TEST_OWNER_EMAIL);await page.locator('[name=password]').fill('AdminFixture123!');await Promise.all([page.waitForURL('**/admin'),page.locator('button[type=submit]').click()]);
  for(const lang of ['en','ar'])for(const theme of ['light','dark'])for(const width of [1440,360]){
   await page.setViewportSize({width,height:900});await page.goto(`http://127.0.0.1:8004/admin?lang=${lang}&theme=${theme}#admin-events`);
   const create=page.locator('.admin-event-create');await create.waitFor({state:'visible'});await create.locator('summary').click().catch(()=>{});if(!await create.locator('select[name=lesson_id]').isVisible())await create.locator('summary').click();
   check((await create.locator('summary').innerText()).includes(lang==='en'?'Create an event':'إنشاء حدث'),'Localized event creation');
   await create.locator('[name=lesson_id]').selectOption({index:1});await create.locator('[name=scheduled_local]').fill('2027-06-20T14:30:59.123');await create.locator('[name=status]').selectOption('scheduled');
   check(!await create.locator('[data-event-confirm]').isChecked()&&await create.locator('[data-event-confirm]').isDisabled(),'Scheduling never fabricates a live confirmation');
   const identifier=await create.locator('[name=lesson_id]').inputValue();await Promise.all([page.waitForNavigation(),create.locator('button.button-primary').click()]);
   const saved=page.locator(`[data-event-record="${identifier}"]`);check(await saved.isVisible(),'Created event reopens its focused editor');
   check(await saved.locator('[name=scheduled_local]').inputValue()==='2027-06-20T14:30:59.123'&&await saved.locator('[name=schedule_offset]').inputValue()==='+00:00','Exact milliseconds and UTC offset survive browser create/reopen');
   await saved.locator('[name=status]').selectOption('live');check(await saved.locator('[data-event-confirm]').evaluate(e=>e.required&&!e.disabled&&!e.checked),'Live state requires an unchecked explicit confirmation');
   await saved.locator('[name=status]').selectOption('scheduled');await saved.locator('[name=title_en]').fill('Browser event translation');await Promise.all([page.waitForNavigation(),saved.locator('button.button-primary').click()]);
   const reopened=page.locator(`[data-event-record="${identifier}"]`);check(await reopened.locator('[name=title_en]').inputValue()==='Browser event translation','Edit saves and reopens the real translation');
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Event forms have no horizontal overflow');
   await reopened.locator('.event-schedule-fields').screenshot({path:path.join(root,`storage/engineering/events-${lang}-${theme}-${width}.png`)});
  }
  const editor=page.locator('[data-event-record]').first();await editor.locator('[name=schedule_timezone]').fill('Invalid/Zone');await Promise.all([page.waitForNavigation(),editor.locator('button.button-primary').click()]);
  check(await page.locator('[data-event-record]').first().locator('[name=schedule_timezone]').inputValue()==='Invalid/Zone','Invalid event draft is retained');await page.waitForFunction(()=>document.activeElement?.hasAttribute('data-admin-form-error'));check(errors.length===0,'No event JavaScript errors');console.log(`PASS: ${checks} actual event browser assertions, AR/EN light/dark desktop/phone.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});

const path = require('node:path');
const {chromium} = require(process.env.ATHAR_TEST_PLAYWRIGHT || 'playwright');
const root = path.resolve(__dirname,'..');
if(root!==path.resolve(process.env.ATHAR_RECOVERY_SANDBOX||''))throw Error('Copied fixture required');
let checks=0;
function check(ok,label){if(!ok)throw Error(label);checks++;}
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const context=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
 const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto('http://127.0.0.1:8004/admin/login?lang=en');
  await page.locator('[name=email]').fill(process.env.ATHAR_TEST_OWNER_EMAIL);await page.locator('[name=password]').fill('AdminFixture123!');
  await Promise.all([page.waitForURL('**/admin'),page.locator('button[type=submit]').click()]);
  for(const lang of ['en','ar'])for(const theme of ['light','dark'])for(const width of [1440,360]){
   await page.setViewportSize({width,height:900});
   await page.goto(`http://127.0.0.1:8004/admin?lang=${lang}&theme=${theme}#admin-create`);
   const card=page.locator('.admin-scholar-create');await card.waitFor({state:'visible'});
   check(await card.getByRole('heading',{name:lang==='en'?'Add a scholar':'إضافة عالم'}).isVisible(),'Localized scholar entry');
   const name=`Browser scholar ${lang} ${theme} ${width}`;
   await card.locator('[name=name]').fill(name);await card.locator('[name=biography]').fill('Generated browser fixture only.');
   await card.locator('[name=source_url]').fill('https://example.invalid/fixture');await card.locator('[name=rights_basis]').fill('Fixture permission');
   await card.locator('[name=language]').selectOption('en');await card.locator('[name=sort_order]').fill('-8');
   check((await card.locator('[name=scholar_image]').getAttribute('required'))===null,'Portrait is optional');
   await card.screenshot({path:path.join(root,`storage/engineering/scholar-create-${lang}-${theme}-${width}.png`)});
   await Promise.all([page.waitForNavigation(),card.locator('button.button-primary').click()]);
   const row=page.locator('.inventory-row').filter({has:page.locator('p').filter({hasText:name})});
   check(await row.isVisible(),'Saved draft is visible in content inventory');
   await row.locator('summary').click();
   check(await row.locator('[name=biography]').inputValue()==='Generated browser fixture only.','Editor preserves biography');
   check(await row.locator('[name=source_url]').inputValue()==='https://example.invalid/fixture'&&await row.locator('[name=rights_basis]').inputValue()==='Fixture permission','Editor preserves source and rights');
   check(await row.locator('[name=language]').inputValue()==='en'&&await row.locator('[name=sort_order]').inputValue()==='-8','Editor preserves language and order');
   check(await row.locator('[name=sort_order]').getAttribute('dir')==='ltr','Numeric order has unambiguous direction in RTL');
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No horizontal overflow');
   if(width===360)await row.screenshot({path:path.join(root,`storage/engineering/scholar-edit-${lang}-${theme}.png`)});
  }
  await page.goto('http://127.0.0.1:8004/admin?lang=en#admin-create');
  const invalid=page.locator('.admin-scholar-create');
  await invalid.locator('[name=name]').fill('Preserved invalid draft');
  await invalid.locator('[name=source_url]').fill('https://user:pass@example.invalid');
  await Promise.all([page.waitForNavigation(),invalid.locator('button.button-primary').click()]);
  check(await page.locator('.admin-scholar-create').isVisible(),'Invalid create remains in its own panel');
  check(await page.locator('.admin-scholar-create [name=name]').inputValue()==='Preserved invalid draft','Server validation preserves draft fields');
  await page.waitForFunction(()=>document.activeElement?.hasAttribute('data-admin-form-error'));
  check((await page.locator('[data-admin-form-error]').innerText()).includes('HTTPS source without URL credentials'),'Localized validation is focused');
  check(errors.length===0,'No scholar browser JavaScript errors');
  console.log(`PASS: ${checks} actual scholar browser assertions, AR/EN light/dark desktop/phone create/edit journeys.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});

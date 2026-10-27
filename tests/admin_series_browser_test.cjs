const path=require('node:path');
const {chromium}=require(process.env.ATHAR_TEST_PLAYWRIGHT||'playwright');
const root=path.resolve(__dirname,'..');if(root!==path.resolve(process.env.ATHAR_RECOVERY_SANDBOX||''))throw Error('Copied fixture required');
let checks=0;function check(ok,label){if(!ok)throw Error(label);checks++;}
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 const context=await browser.newContext({viewport:{width:1440,height:900},reducedMotion:'reduce'});const page=await context.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 try{
  await page.goto('http://127.0.0.1:8004/admin/login?lang=en');await page.locator('[name=email]').fill(process.env.ATHAR_TEST_OWNER_EMAIL);await page.locator('[name=password]').fill('AdminFixture123!');await Promise.all([page.waitForURL('**/admin'),page.locator('button[type=submit]').click()]);
  for(const lang of ['en','ar'])for(const theme of ['light','dark'])for(const width of [1440,360]){
   await page.setViewportSize({width,height:900});await page.goto(`http://127.0.0.1:8004/admin?lang=${lang}&theme=${theme}#admin-create`);
   const card=page.locator('.admin-series-create');await card.waitFor({state:'visible'});
   check(await card.getByRole('heading',{name:lang==='en'?'Add a series':'إضافة سلسلة'}).isVisible(),'Localized series creation');
   const title=`Browser series ${lang} ${theme} ${width}`;
   await card.locator('[name=title]').fill(title);await card.locator('[name=description]').fill('Generated series browser fixture.');await card.locator('[name=source_url]').fill('https://example.invalid/series');await card.locator('[name=scholar_id]').selectOption('1');await card.locator('[name=sort_order]').fill('-9');
   await card.screenshot({path:path.join(root,`storage/engineering/series-create-${lang}-${theme}-${width}.png`)});
   await Promise.all([page.waitForNavigation(),card.locator('button.button-primary').click()]);
   const row=page.locator('.inventory-row').filter({has:page.locator('p').filter({hasText:title})});check(await row.isVisible(),'Saved series visible in content inventory');
   check((await row.locator('p').first().innerText()).includes(lang==='en'?'Draft':'مسودة'),'Series publication status is localized and truthful');await row.locator('summary').click();
   check(await row.locator('[name=description]').inputValue()==='Generated series browser fixture.','Description survives editor reopening');
   check(await row.locator('[name=source_url]').inputValue()==='https://example.invalid/series','Source survives editor reopening');
   check(await row.locator('[name=scholar_id]').inputValue()==='1'&&await row.locator('[name=sort_order]').inputValue()==='-9','Scholar and genuine order survive editor reopening');
   check(await row.locator('[name=sort_order]').getAttribute('dir')==='ltr','Numeric direction remains clear in RTL');
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No horizontal overflow');
   if(width===360)await row.screenshot({path:path.join(root,`storage/engineering/series-edit-${lang}-${theme}.png`)});
  }
  await page.goto('http://127.0.0.1:8004/admin?lang=en#admin-create');const invalid=page.locator('.admin-series-create');await invalid.locator('[name=title]').fill('Preserved series draft');await invalid.locator('[name=source_url]').fill('https://user:pass@example.invalid');await Promise.all([page.waitForNavigation(),invalid.locator('button.button-primary').click()]);
  check(await page.locator('.admin-series-create').isVisible()&&await page.locator('.admin-series-create [name=title]').inputValue()==='Preserved series draft','Invalid series draft retains panel and entered fields');await page.waitForFunction(()=>document.activeElement?.hasAttribute('data-admin-form-error'));check((await page.locator('[data-admin-form-error]').innerText()).includes('HTTPS source without URL credentials'),'Localized series error receives focus');
  check(errors.length===0,'No series browser JavaScript errors');console.log(`PASS: ${checks} actual series browser assertions, AR/EN light/dark desktop/phone create/edit.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});

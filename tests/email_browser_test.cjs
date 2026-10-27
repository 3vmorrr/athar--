const {chromium}=require(process.env.ATHAR_TEST_PLAYWRIGHT),fs=require('fs'),path=require('path');
const root=path.resolve(__dirname,'..');if(root!==path.resolve(process.env.ATHAR_RECOVERY_SANDBOX||''))throw Error('Owned copied fixture required');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});let checks=0;
 const check=(ok,label)=>{if(!ok)throw Error(label);checks++;};
 try{
  const page=await browser.newPage({reducedMotion:'reduce'});
  await page.route('https://example.invalid/**',route=>route.fulfill({path:path.join(root,'public/assets/images/athar-logo-cropped.png'),contentType:'image/png'}));
  for(const [locale,width] of [['ar',360],['en',1440],['en',360]]){
   await page.setViewportSize({width,height:1000});await page.setContent(fs.readFileSync(path.join(root,`storage/engineering/email-preview-${locale}.html`),'utf8'));
   check(await page.locator('html').getAttribute('dir')===(locale==='ar'?'rtl':'ltr'),'Correct mail direction');
   check(await page.locator('img').evaluate(e=>e.complete&&e.naturalWidth>0),'Actual local Athar logo renders');
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Mail has no horizontal overflow');
   await page.screenshot({path:path.join(root,`storage/engineering/email-branded-${locale}-${width}.png`),fullPage:true});
  }
  await page.goto('http://127.0.0.1:8004/admin/login?lang=en');await page.locator('[name=email]').fill(process.argv[2]);await page.locator('[name=password]').fill('AdminFixture123!');await Promise.all([page.waitForURL('**/admin'),page.locator('button[type=submit]').click()]);
  for(const [lang,theme,width] of [['en','light',1440],['ar','dark',360]]){
   await page.setViewportSize({width,height:1000});await page.goto(`http://127.0.0.1:8004/admin?campaign_preview=${process.argv[3]}&lang=${lang}&theme=${theme}#admin-campaigns`);
   check(await page.locator('.admin-campaign-preview').isVisible(),'Campaign saved state visible');
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'Email history no overflow');
   check(await page.locator('#admin-campaigns .button').evaluateAll(items=>items.every(e=>e.offsetParent===null||e.getBoundingClientRect().height>=44)),'Email touch targets');
   await page.locator('#admin-campaigns').screenshot({path:path.join(root,`storage/engineering/email-admin-${lang}-${theme}-${width}.png`)});
  }
  console.log(`PASS: ${checks} branded Email and Admin browser assertions, AR/EN phone/desktop; logo fulfilled from fixture, no provider request.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});

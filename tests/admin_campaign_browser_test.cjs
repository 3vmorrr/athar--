const {chromium}=require(process.env.ATHAR_TEST_PLAYWRIGHT);
const path=require('path');
const root=process.env.ATHAR_RECOVERY_SANDBOX;
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});
 let checks=0;
 const check=(ok,label)=>{if(!ok)throw Error(label);checks++;};
 try {
  const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('http://127.0.0.1:8004/admin/login?lang=en');await page.locator('[name=email]').fill(process.argv[2]);await page.locator('[name=password]').fill('AdminFixture123!');await page.locator('button[type=submit]').click();
  for(const [width,lang,theme] of [[1440,'en','light'],[360,'ar','dark'],[360,'en','dark'],[1440,'ar','light']]) {
   await page.setViewportSize({width,height:1000});await page.goto(`http://127.0.0.1:8004/admin?lang=${lang}&theme=${theme}#admin-campaigns`);
   check(await page.locator('[data-campaign-editor]').isVisible(),'Composer visible');
   check(await page.locator('[data-admin-nav] a[href^="#admin-"]').evaluateAll(links=>links.every(link=>document.querySelector(link.hash)!==null)),'Every adjacent Admin navigation target exists');
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),'No overflow');
   const form=page.locator('[data-campaign-editor]');
   await form.locator('[name=name]').fill(`Browser campaign ${lang} ${width}`);
   await form.locator('[name=channel]').selectOption('push');
   await form.locator('[name=title_ar]').fill('عنوان تجريبي');await form.locator('[name=body_ar]').fill('نص تجريبي غير ديني');
   await form.locator('[name=title_en]').fill('Browser title');await form.locator('[name=body_en]').fill('Browser test content');
   await form.locator('button[type=submit]').click();
   check(page.url().includes('campaign_preview='),'Save routes to actual saved preview');
   check(await page.locator('.admin-campaign-preview').innerText().then(t=>t.includes('Browser title')&&t.includes('عنوان تجريبي')),'Bilingual preview');
   check(await page.locator('.admin-campaign-preview button').first().isDisabled(),'Unavailable Push cannot be confirmed');
   check(await page.locator('.admin-campaign-preview [name=owner_password]').inputValue()==='','Password never reflected');
   await page.locator('#admin-campaigns').screenshot({path:path.join(root,`storage/engineering/campaign-${lang}-${theme}-${width}.png`)});
  }
  check(errors.length===0,'No browser errors');
  console.log(`PASS: ${checks} campaign browser assertions, AR/EN light/dark desktop/phone. No provider send.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});

const path=require('node:path'),{chromium}=require(process.env.ATHAR_TEST_PLAYWRIGHT||'playwright');
const root=path.resolve(__dirname,'..');if(root!==path.resolve(process.env.ATHAR_RECOVERY_SANDBOX||''))throw Error('Copied fixture required');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true});let checks=0;
 try{
  const page=await browser.newPage({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
  await page.goto('http://127.0.0.1:8004/admin/login?lang=en');await page.locator('[name=email]').fill(process.env.ATHAR_TEST_OWNER_EMAIL);await page.locator('[name=password]').fill('AdminFixture123!');await Promise.all([page.waitForURL('**/admin'),page.locator('button[type=submit]').click()]);
  for(const [width,lang,theme,panel,target] of [[1440,'en','light','admin-create','.admin-book-create'],[1440,'en','dark','admin-night-prayer','#admin-night-prayer'],[360,'ar','dark','admin-create','.admin-book-create']]){
   await page.setViewportSize({width,height:1000});await page.goto(`http://127.0.0.1:8004/admin?lang=${lang}&theme=${theme}#${panel}`);
   if(!await page.locator('.admin-skip-link').evaluate(e=>getComputedStyle(e).clipPath==='inset(50%)'))throw Error('Skip link not visually hidden');checks++;
   if(!await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1))throw Error('Visual overflow');checks++;
   if(!await page.locator('.button').evaluateAll(items=>items.every(e=>e.offsetParent===null||e.getBoundingClientRect().height>=44)))throw Error('Button touch target below44px');checks++;
   await page.locator(target).screenshot({path:path.join(root,`storage/engineering/batch-visual-${panel}-${lang}-${theme}-${width}.png`)});
   await page.locator('.admin-skip-link').focus();if(!await page.locator('.admin-skip-link').evaluate(e=>getComputedStyle(e).clipPath==='none'&&e.getBoundingClientRect().top>=0))throw Error('Focused skip link not visible');checks++;
   await page.keyboard.press('Enter');if(!await page.evaluate(()=>document.activeElement.closest('main')!==null))throw Error('Skip link did not reach main content');checks++;
  }
  console.log(`PASS: ${checks} targeted visual/keyboard assertions and desktop/phone captures; no repeated functional suites.`);
 }finally{await browser.close();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});

const path = require('node:path');
const fs = require('node:fs');
const {chromium} = require(process.env.ATHAR_TEST_PLAYWRIGHT || 'playwright');
const root = path.resolve(__dirname, '..');
if (root !== path.resolve(process.env.ATHAR_RECOVERY_SANDBOX || '') || !/^recovery-http-[a-f0-9]{32}$/.test(path.basename(root))) throw Error('Isolated recovery copy required');
let checks = 0;
function check(ok, label) { if (!ok) throw Error(label); checks++; }
(async () => {
  const browser = await chromium.launch({executablePath: process.env.ATHAR_TEST_BROWSER || 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe', headless: true});
  const context = await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'});
  const page = await context.newPage();
  const errors = [];
  page.on('pageerror', err => errors.push(err.message));
  const base = 'http://127.0.0.1:8004';
  try {
    await page.goto(base + '/admin/login?lang=en');
    await page.locator('[name=email]').fill(process.env.ATHAR_TEST_OWNER_EMAIL);
    await page.locator('[name=password]').fill('AdminFixture123!');
    await Promise.all([page.waitForURL('**/admin'),page.locator('button[type=submit]').click()]);
    check(await page.locator('#admin-overview').isVisible(), 'Real browser Owner sign-in');
    for (const lang of ['en','ar']) for (const theme of ['light','dark']) for (const width of [1440,360]) {
      await page.setViewportSize({width,height:900});
      await page.goto(`${base}/admin?lang=${lang}&theme=${theme}#admin-backups`);
      await page.locator('#admin-backups').waitFor({state:'visible'});
      await page.evaluate(() => document.fonts.ready);
      check(await page.locator('html').getAttribute('dir') === (lang==='ar'?'rtl':'ltr'), 'Recovery direction matches language');
      check(await page.locator('html').getAttribute('data-theme') === theme, 'Recovery theme matches choice');
      check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Recovery has no horizontal overflow');
      check(await page.locator('#admin-backups').getByRole('heading',{name:lang==='en'?'Create a full backup':'إنشاء نسخة كاملة',exact:true}).isVisible(), 'Localized create heading visible');
      check(await page.locator('#admin-backups input[type=password]').first().getAttribute('autocomplete')==='current-password', 'Recovery supports password managers');
      await page.screenshot({path:path.join(root,`storage/engineering/recovery-${lang}-${theme}-${width}.png`)});
      if (width===360) {
        const reset = page.locator('#admin-backups .backup-create-card').last();
        await reset.screenshot({path:path.join(root,`storage/engineering/recovery-reset-${lang}-${theme}.png`)});
      }
    }
    await page.setViewportSize({width:360,height:900});
    await page.goto(`${base}/admin?lang=en&theme=dark#admin-backups`);
    await page.evaluate(() => {document.documentElement.style.fontSize='200%';});
    check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1), 'Recovery adapts to doubled root font size');
    check(await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches), 'Reduced motion preference applied');
    await page.screenshot({path:path.join(root,'storage/engineering/recovery-large-font.png')});
    await page.goto(`${base}/admin?lang=en#admin-backups`);
    const create = page.locator('#admin-backups form').filter({has:page.locator('[name=kind][value=backup-create]')});
    await create.locator('[name=password]').fill('incorrect');
    await create.locator('[name=manifest_confirmation]').fill('CREATE FULL BACKUP');
    await Promise.all([page.waitForNavigation(),create.getByRole('button').click()]);
    await page.locator('[data-admin-form-error]').waitFor({state:'visible'});
    await page.waitForFunction(() => document.activeElement?.hasAttribute('data-admin-form-error'));
    check((await page.locator('[data-admin-form-error]').innerText()).includes('The password is incorrect.'), 'Browser receives localized recovery error');
    check(await page.locator('#admin-backups').isVisible(), 'Form error retains recovery panel');
    const restore = page.locator('#admin-backups form').filter({has:page.locator('[name=kind][value=backup-restore]')});
    await restore.locator('[name=password]').fill('AdminFixture123!');
    await restore.locator('[name=restore_confirmation]').fill('RESTORE FULL BACKUP');
    let posts = 0;
    page.on('request', req => {if(req.method()==='POST')posts++;});
    const dialogSeen = new Promise(resolve => page.once('dialog', async dialog => {
      check(dialog.message().includes('Replace application data'), 'Browser confirms destructive restore');
      await dialog.dismiss(); resolve();
    }));
    await restore.getByRole('button').click(); await dialogSeen;
    await page.waitForTimeout(150);
    check(posts===0, 'Cancelling browser confirmation sends no destructive request');
    check(errors.length===0, 'Recovery has no browser JavaScript errors');
    fs.writeFileSync(path.join(root,'storage/engineering/recovery-browser-result.json'),JSON.stringify({checks,passed:true,viewports:[1440,360],languages:['ar','en'],themes:['light','dark'],reducedMotion:true},null,2));
    console.log(`PASS: ${checks} actual browser recovery assertions; AR/EN light/dark desktop/phone, large fonts, reduced motion, error focus and cancellation.`);
  } finally { await browser.close(); }
})().catch(error => {console.error(error.message);process.exitCode=1;});

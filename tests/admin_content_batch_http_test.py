"""One combined WP18 content/services gate with one browser setup."""
import os
import subprocess
import admin_book_http_test as books
import admin_editorial_http_test as editorial
import admin_services_http_test as services
import admin_night_defaults_http_test as night
import admin_batch_fixture as fixture

total=books.checks+editorial.checks+services.checks+night.checks
print(f'PASS: {total} combined WP18 book/editorial/moderation/services/public-defaults assertions.')
if os.environ.get('ATHAR_TEST_NODE'):
    subprocess.run([os.environ['ATHAR_TEST_NODE'],str(fixture.root/'tests/admin_content_batch_browser_test.cjs')],env=dict(os.environ,ATHAR_TEST_OWNER_EMAIL=night.owner.email,ATHAR_TEST_COMMENT_ID=str(editorial.api_comment),ATHAR_TEST_DELETE_COMMENT_ID=str(editorial.new_comment())),check=True,timeout=240)

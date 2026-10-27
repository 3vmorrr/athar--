"""Small screenshot/keyboard pass; no repeated functional regression setup."""
import os
import subprocess
import admin_batch_fixture as fixture
owner=fixture.Session('owner')
subprocess.run([os.environ['ATHAR_TEST_NODE'],str(fixture.root/'tests/admin_content_visual_test.cjs')],env=dict(os.environ,ATHAR_TEST_OWNER_EMAIL=owner.email),check=True,timeout=90)

"""Render/validate the explicit 149-task source audit; never infer verdicts from file presence.

Verdicts and scope findings are human-reviewed in ACTUAL_IMPLEMENTATION_REVIEW.tsv.
This tool resolves evidence, fingerprints source/test receipts, derives totals and reconciles
the queue/ledger. It never edits application code or runs an acceptance test.
"""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PROGRESS = ROOT / 'docs/progress'
PARALLEL = Path('C:/xampp/athar-parallel')
BASELINE_SHA = '2b7d7de0fc3a6cdf2fdfb99adbd3ee6c5c11a474'
BASELINE_AUDIT_COMMIT = '9492f1453e3a1dccb7d80e161deff03b83273f6b'
INPUT = PROGRESS/'ACTUAL_IMPLEMENTATION_AUDIT_INPUT.json'
CHECKPOINT = json.loads(INPUT.read_text(encoding='utf-8')) if INPUT.exists() else {}
TASK_CHECKPOINTS = CHECKPOINT.get('prior_checkpoints', []) + ([CHECKPOINT] if CHECKPOINT else [])
TARGET = CHECKPOINT.get('main_sha', BASELINE_SHA)
SOURCE = '7392e93b088990c3478cf32197781f831eb10f83'
DATE = CHECKPOINT.get('date', '2026-10-29')
AUDIT = PROGRESS / 'ACTUAL_IMPLEMENTATION_AUDIT.json'
RECONCILED_DOCS = [
    'docs/progress/TASK_TRACKER.md','docs/progress/IMPLEMENTATION_PLAN.md',
    'docs/progress/VALIDATION_STRATEGY.md','docs/progress/CHANGELOG.md',
    'docs/progress/DECISION_LOG.md','docs/progress/TASK_COMPLETIONS.md',
    'docs/testing/ACCOUNT_FUNCTIONAL_INTEGRATION.md','docs/testing/QURAN_FUNCTIONAL_INTEGRATION.md',
    'docs/testing/AUDIO_FUNCTIONAL_INTEGRATION.md','docs/testing/ADHKAR_FUNCTIONAL_INTEGRATION.md',
    'docs/testing/BOOKS_FUNCTIONAL_INTEGRATION.md','docs/testing/OS_SYNC_LIFECYCLE.md',
    'docs/testing/ADMIN_EVENTS.md','docs/testing/ADMIN_CONTENT_SERVICES_BATCH.md',
    'docs/testing/ADMIN_CAMPAIGNS.md','docs/testing/EMAIL_OUTBOX.md',
    'docs/testing/TEST_REPORT.md','docs/testing/PHYSICAL_DEVICE_REPORT.md',
]

def git(root, *args):
    return subprocess.check_output(['git', '-C', str(root), *args], text=True, encoding='utf-8').strip()

def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def fingerprint(path):
    raw = path.read_bytes()
    return {'path': path.relative_to(ROOT).as_posix(), 'sha256': hashlib.sha256(raw).hexdigest(), 'bytes': len(raw)}

def tracked(root):
    return git(root, 'ls-files').splitlines()

TRACKED = {'main': tracked(ROOT), 'parallel': tracked(PARALLEL)}
EVIDENCE_CACHE = {}
EVIDENCE_CACHE_PATH = ROOT/'artifacts/functional-integration/audit-source-cache.json'
if EVIDENCE_CACHE_PATH.exists():
    for key, value in json.loads(EVIDENCE_CACHE_PATH.read_text(encoding='utf-8')).items():
        location, token = key.split('|',1)
        candidate = (ROOT if location=='main' else PARALLEL)/value['path']
        if candidate.is_file() and hashlib.sha256(candidate.read_bytes()).hexdigest()==value.get('sha256'):
            EVIDENCE_CACHE[(token,location)] = value

def evidence_file(token, location):
    if (token,location) in EVIDENCE_CACHE: return EVIDENCE_CACHE[(token,location)]
    root = ROOT if location == 'main' else PARALLEL
    candidates = [p for p in TRACKED[location] if p == token or p.endswith('/' + token)]
    exact = [p for p in candidates if p == token]
    if exact: candidates = exact
    if len(candidates) != 1:
        raise ValueError(f'{location}: ambiguous/missing evidence {token}: {candidates}')
    path = candidates[0]
    absolute = root / path
    if absolute.is_dir():
        return {'location': location, 'path': path, 'kind': 'test tree', 'ref': TARGET if location == 'main' else SOURCE}
    content = absolute.read_text(encoding='utf-8-sig')
    lines = content.splitlines()
    symbols = []
    for n, line in enumerate(lines, 1):
        if re.search(r'\b(fun|class|object|interface|function)\s+\w+|^CREATE TABLE|^ALTER TABLE', line):
            symbols.append({'line': n, 'symbol': line.strip()[:220]})
    ref = TARGET if location == 'main' else SOURCE
    blob = git(root, 'rev-parse', ref + ':' + path)
    result = {'location': location, 'path': path, 'ref': ref, 'git_blob': blob,
            'sha256': hashlib.sha256(absolute.read_bytes()).hexdigest(),
            'last_path_commit': git(root, 'log', '-1', '--format=%H', ref, '--', path),
            'symbols': symbols or [{'line': 1, 'symbol': lines[0][:220] if lines else '(empty)'}],
            'worktree_matches_ref': git(root, 'hash-object', '--', path) == blob}
    EVIDENCE_CACHE[(token,location)] = result
    return result

ACCEPTED_LOGS = {
    '02.1': ['protected-local-final-storage.log', 'protected-local-physical-ar.log', 'protected-local-physical-en.log'],
    '02.2': ['persistent-sync-physical-network-aware.log', 'persistent-sync-physical-storage.log', 'persistent-sync-ui-ar.log', 'persistent-sync-ui-en.log'],
    '02.7.2': ['night-rejection-final-ar.log', 'night-rejection-final-en.log'],
    '17.1': ['email-admin-privacy-gate.log'], '17.2': ['admin-shell-http.log', 'email-admin-privacy-gate.log'],
    '17.3': ['email-admin-privacy-gate.log'], '17.4': ['email-admin-privacy-gate.log'],
    '17.5': ['email-admin-privacy-gate.log'],
    '17.6': ['admin-recovery-maintenance-http.log', 'admin-recovery-journal-http.log', 'admin-recovery-reset-backend.log'],
    '18.1': ['admin-scholars-http.log', 'admin-scholar-lesson-regression-http.log'],
    '18.2': ['admin-series-final-http.log'], '18.3': ['admin-lessons-http.log', 'admin-wp18-backend-gate.log'],
    '18.5': ['admin-content-batch.log'], '18.7': ['admin-content-batch.log'], '18.8': ['admin-content-batch.log'],
    '20.1': ['email-wp20-final-http.log', 'email-outbox-locale-final.log'],
    '20.2': ['email-wp20-final-http.log', 'email-outbox-locale-final.log'],
    '20.3': ['email-wp20-final-http.log', 'email-outbox-locale-final.log'],
}

JVM_PATTERNS = {
    '02': 'ProtectedRead|ContentCache|SessionRepository|OwnedSyncRunner|NightRejection',
    '03': 'Quran|DailyAyah|Tafsir', '04': 'Audio|Playback|PlayerPolicy',
    '05': 'Adhkar|CounterDay', '06': 'Book', '07': 'Account|SavedCollections|ActivitySummary|BookCompletion',
    '08': 'Domain|NightRejection', '21': 'HomeProjection|ImmersiveHero|PlayerPolicy',
    '22': 'ContentCache|BookFiles', '23': 'Account|PlaybackOwnership|SessionRepository|BookFiles',
}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    queue_path = PROGRESS / 'TASK_QUEUE.json'
    queue = json.loads(queue_path.read_text(encoding='utf-8'))
    for task in queue['tasks']:
        if task['automated_tests'].startswith('COMPLETE:'):
            task['historical_automated_gate_receipt']=task['automated_tests']
            task['automated_tests']='Historical executed evidence (not current closure authority):'+task['automated_tests'][len('COMPLETE:'):]
        if task['id']=='21.15':
            task['automated_tests']=task['automated_tests'].replace('292+ resource format checks','Current paired resource format checks (432 at audited source)')
            if CHECKPOINT:
                task['automated_tests']=re.sub(r'Current paired resource format checks \([^)]*\)',f"Current paired resource format checks ({CHECKPOINT['validation']['resources_paired']} at current checkpoint)",task['automated_tests'])
    previous = json.loads(AUDIT.read_text(encoding='utf-8')) if AUDIT.exists() else None
    old = {t['id']: t['old_status'] for t in previous['tasks']} if previous else {t['id']: t['state'] for t in queue['tasks']}
    reviewed = {}
    for line in (PROGRESS / 'ACTUAL_IMPLEMENTATION_REVIEW.tsv').read_text(encoding='utf-8').splitlines():
        if not line or line.startswith('#'): continue
        fields = line.split('|')
        assert len(fields) == 5, line
        task_id, verdict, paths, present, missing = fields
        assert task_id not in reviewed and verdict in 'AVPUR MSBN'.replace(' ', ''), task_id
        reviewed[task_id] = (verdict, paths.split(','), present, missing)
    assert len(reviewed) == 149 and set(reviewed) == {t['id'] for t in queue['tasks']}
    # Verify the Markdown task definitions were actually kept in sync with the reviewed JSON scope.
    queue_md = (PROGRESS / 'TASK_QUEUE.md').read_text(encoding='utf-8')
    assert all(t['scope'] in queue_md and t['definition_of_done'] in queue_md for t in queue['tasks'])
    junit = []
    baseline_audit = None
    if CHECKPOINT:
        # A targeted later suite replaces the local XML directory. Never relabel it
        # as the original167-case run or silently discard the original receipt.
        baseline_audit=json.loads(git(ROOT,'show',BASELINE_AUDIT_COMMIT+':docs/progress/ACTUAL_IMPLEMENTATION_AUDIT.json'))
        junit=baseline_audit['current_validation']['junit_receipts']
    else:
        for path in sorted((ROOT / 'android/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
            x = ET.parse(path).getroot()
            junit.append(dict(name=x.attrib['name'], tests=int(x.attrib['tests']), failures=int(x.attrib['failures']),
                              errors=int(x.attrib['errors']), skipped=int(x.attrib.get('skipped', 0)), **fingerprint(path)))
    assert len(junit) == 38 and sum(x['tests'] for x in junit) == 167
    assert not any(x['failures'] or x['errors'] or x['skipped'] for x in junit)
    backend_receipt=(ROOT/'artifacts/functional-integration/book-activity-serial-backend.log').read_text(encoding='utf-8-sig')
    banners=[int(n.replace(',','')) for n in re.findall(r'^PASS: ([0-9,]+)',backend_receipt,re.M)]
    assert len(banners)==25 and sum(banners)-15==21192
    assert backend_receipt.count('PASS: 15 provider catalog/recording/timing/URL-boundary assertions')==2
    lint=ET.parse(ROOT/'android/app/build/reports/lint-results-debug.xml').getroot()
    assert not any(i.get('severity') in {'Error','Fatal'} for i in lint.findall('issue'))
    assert sum(i.get('severity')=='Warning' for i in lint.findall('issue'))==CHECKPOINT.get('validation',{}).get('lint_warnings',59)
    assert 'BUILD SUCCESSFUL' in (ROOT/'artifacts/functional-integration/book-activity-serial-gradle.log').read_text(encoding='utf-8-sig')
    assert 'OK (35 tests)' in (ROOT/'artifacts/functional-integration/book-activity-api34.private.log').read_text(encoding='utf-8-sig')
    receipts = (PROGRESS / 'TASK_COMPLETIONS.md').read_text(encoding='utf-8')
    rows = []
    unresolved = []
    for task in queue['tasks']:
        task_id = task['id']; wp = task_id[:2]
        verdict, paths, present, missing = reviewed[task_id]
        location = 'parallel' if verdict in 'URSB' else 'main'
        # These external gates have local implemented mechanisms, but are not completed gate outcomes.
        if task_id in {'03.8', '20.5', '24.1'}: location = 'main'
        evidence = []
        for token in paths:
            if token in {'android/app/src/androidTest/java/org/athar/app', 'tests'}:
                evidence.append({'location': location, 'path': token, 'kind': 'test tree', 'ref': TARGET if location == 'main' else SOURCE})
            else:
                try: evidence.append(evidence_file(token, location))
                except ValueError as error: unresolved.append(str(error))
        for token in CHECKPOINT.get('additional_main_evidence',{}).get(task_id,[]):
            evidence.append(evidence_file(token,'main'))
        implementation = {'A':'IMPLEMENTED','V':'IMPLEMENTED','P':'PARTIALLY_IMPLEMENTED',
                          'U':'IMPLEMENTED','R':'PARTIALLY_IMPLEMENTED','S':'SCAFFOLDED','M':'SCAFFOLDED',
                          'B':'IMPLEMENTATION_BLOCKED','N':'NOT_IMPLEMENTED'}[verdict]
        if task_id in {'15.3', '20.5'}: implementation = 'SCAFFOLDED'
        integration = 'INTEGRATED' if verdict in 'AV' else ('PARTIALLY_INTEGRATED' if verdict == 'P' else 'NOT_INTEGRATED')
        if task_id in {'03.8', '15.3', '24.1'}: integration = 'INTEGRATION_BLOCKED'
        integration=CHECKPOINT.get('integration_overrides',{}).get(task_id,integration)
        verification = 'ACCEPTED' if verdict == 'A' else ('ACCEPTANCE_PENDING' if verdict == 'V' else 'NOT_TESTED')
        if verdict in 'URS' and any(p.endswith('.kt') or p.endswith('.kts') or p.endswith('AndroidManifest.xml') for p in paths):
            verification = 'TESTS_FAILING'
        if task_id == '15.1': verification = 'TESTS_FAILING'
        if verdict == 'B': verification = 'VERIFICATION_BLOCKED'
        closure = 'COMPLETE' if verdict == 'A' else ('BLOCKED' if verdict == 'B' else ('NOT_STARTED' if verdict == 'N' else 'PARTIAL'))
        category = {'A':'accepted', 'V':'implemented_integrated_awaiting_acceptance',
                    'U':'implemented_unmerged', 'P':'partially_implemented', 'R':'partially_implemented',
                    'S':'scaffold_or_unmeasured_gate','M':'scaffold_or_unmeasured_gate', 'B':'external_blocked', 'N':'not_implemented'}[verdict]
        tests = []
        if location == 'main' and wp in JVM_PATTERNS:
            tests.append({'kind':'inherited_baseline_JVM_subset', 'source_ref':BASELINE_SHA,
                          'classes':[x['name'] for x in junit if re.search(JVM_PATTERNS[wp],x['name'])],
                          'limit':'Passed unit subset only; does not establish native API/physical/provider/final acceptance.'})
        if location == 'main' and wp in {'03','04','05','06','07','08','17','18','19','20','22','23'}:
            tests.append({'kind':'inherited_baseline_backend_subsystem_gate', 'source_ref':BASELINE_SHA,
                          'receipt':'artifacts/functional-integration/book-activity-serial-backend.log',
                          'limit':'24 executed suites/21192 assertions overall, not per-task assertion counts; mocks do not prove provider delivery.'})
        log_receipts = []
        for name in ACCEPTED_LOGS.get(task_id, []):
            path = ROOT / 'storage/engineering' / name
            assert path.is_file(), name
            raw = path.read_text(encoding='utf-8-sig', errors='replace')
            passed = bool(re.search(r'PASS|OK \(',raw))
            assert passed, name
            log_receipts.append(dict(**fingerprint(path), passed_receipt=True, provenance='inherited; not executed by this audit'))
        receipt_line = next((s for s in receipts.splitlines() if s.startswith('|'+task_id+' ')), '')
        if task_id in {'18.5','18.7','18.8'}:
            receipt_line = next(s for s in receipts.splitlines() if s.startswith('|18.5/'))
        if task_id in {'20.1','20.2','20.3'}:
            receipt_line = next(s for s in receipts.splitlines() if s.startswith('|20.1/'))
        prior_commits = re.findall(r'\b[a-f0-9]{40}\b', receipt_line)
        continuity = []
        for commit in prior_commits:
            git(ROOT, 'cat-file', '-e', commit + '^{commit}')
            for item in evidence:
                if item['location'] != 'main' or 'git_blob' not in item: continue
                result = subprocess.run(['git','-C',str(ROOT),'rev-parse',commit+':'+item['path']],capture_output=True,text=True)
                continuity.append({'receipt_commit':commit,'path':item['path'],
                                   'same_blob':result.returncode == 0 and result.stdout.strip() == item['git_blob']})
        if verdict == 'A' and task_id != '21.18': assert log_receipts, task_id
        if task_id == '21.18':
            tests.append({'kind':'documentation_scope_review','limit':'No native implementation claimed; design specifications, links and queue scope reviewed.'})
        if verdict in 'URS':
            tests.append({'kind':'new_parallel_configuration_failure','receipt':'artifacts/functional-integration/audit-parallel-configuration.log',
                          'limit':'Gradle :app:tasks fails at SourceSet Provider configuration. Local pre-existing File/import repair present; pristine branch has an additional script compilation issue. No JVM/instrumentation pass on this source claimed.'})
        if task_id == '15.1':
            tests.append({'kind':'new_adapter_test_failure','receipt':'artifacts/functional-integration/audit-push-provider.log',
                          'limit':'PHP8.0.30 parse error on readonly property in PushHttpResponse; zero test assertions reached. Requires explicit supported runtime reconciliation, not fabricated pass.'})
        alternative_evidence=[]
        if task_id in {'09.2','09.3'}:
            name='ReviewModelDownload.kt' if task_id=='09.2' else 'ReviewAudioCapture.kt'
            path='android/app/src/main/java/org/athar/app/review/'+name
            alternative='origin/chatgpt/athar-premium-ui-v3'
            ref=git(ROOT,'rev-parse',alternative)
            alternative_evidence.append({'branch':alternative,'ref':ref,'path':path,
                'git_blob':git(ROOT,'rev-parse',ref+':'+path),
                'finding':'Worker coroutine cancellation/current WorkManager API correction' if task_id=='09.2' else 'Uses actual AudioFormat.ENCODING_PCM_16BIT constant',
                'integration_status':'NOT_INTEGRATED','verification':'Source diff reviewed; no successful Android build/physical inference claim.'})
        if task_id in {'09.1','09.2','09.3','24.3'}:
            alternative='origin/chatgpt/athar-premium-ui-v3'
            ref=git(ROOT,'rev-parse',alternative)
            path='android/app/build.gradle.kts'
            alternative_evidence.append({'branch':alternative,'ref':ref,'path':path,
                'git_blob':git(ROOT,'rev-parse',ref+':'+path),
                'finding':'Existing GenerateReviewModelAssetsTask and androidComponents Variant API addGeneratedSourceDirectory fix the inspected SourceSet Provider registration pattern; File namespace fixes also exist.',
                'integration_status':'NOT_INTEGRATED','verification':'Static diff reviewed only. The failed configuration run was7392 working tree; this later branch was not built by the audit.'})
        if verdict == 'B':
            external = {'03.8':'Approved Dorar provider access/contract', '15.3':'Authorized FCM project and physical target',
                        '20.5':'Authorized Gmail recipient/configuration and observed inbox', '24.1':'Authorized compatible free host account/deployment'}[task_id]
        else: external = None
        feature_code = (verdict in 'PRN' and task_id != '23.5') or task_id == '03.8'
        integration_code = verdict in 'UR' or task_id in {'24.2','24.3','24.5'}
        old_accurate = (old[task_id].replace(' ', '_') == closure)
        if old[task_id] == 'PARTIAL':
            accuracy = 'Closure remains open, but old PARTIAL hid finished code/acceptance-only work.' if verdict == 'V' else 'Closure remains open; concrete code/integration/measurement deficit is now explicit.'
        elif old[task_id] == 'NOT STARTED' and verdict != 'N':
            accuracy = 'Misleading as an implementation statement: code/tooling exists, predominantly unmerged. No integration credit granted.'
        elif old[task_id] == 'COMPLETE' and verdict != 'A':
            accuracy = 'Reopened for current acceptance: relevant production call paths changed after the old hardware receipt.'
        else: accuracy = 'Retained only for the bounded scope supported by source and receipts; not a release-readiness claim.'
        for checkpoint in TASK_CHECKPOINTS:
            if task_id in checkpoint.get('reviewed_tasks',[]):
                accuracy='Implementation advanced after original audit9492f14. Earlier partial finding was accurate then; this record reflects the later functional checkpoint, not retroactive completion.'
                tests.append({'kind':'new_focused_functional_checkpoint','source_ref':checkpoint['main_sha'],'result':checkpoint['validation']})
        main_status = implementation if location == 'main' else ('PARTIALLY_IMPLEMENTED' if task_id[:2] in {'07','08','21'} else 'NOT_IMPLEMENTED')
        rows.append({'id':task_id,'name':task['name'],'scope':task['scope'],'definition_of_done':task['definition_of_done'],
                     'old_status':old[task_id],'implementation_status':implementation,'implementation_location':location,
                     'main_implementation_status':main_status,'integration_status':integration,'verification_status':verification,
                     'closure_status':closure,'exclusive_category':category,
                     'implemented_requirements':[present], 'missing_requirements':[] if verdict=='A' else [missing],
                     'exact_remaining_work':[] if verdict=='A' else [missing],
                     'feature_code_remaining':feature_code,'integration_code_remaining':integration_code,
                     'test_code_remaining':task_id=='23.5',
                     'production_code_remaining':feature_code or integration_code,
                     'acceptance_only_in_current_app':verdict=='V',
                     'evidence':evidence,'related_commits':sorted(set(prior_commits+[e['last_path_commit'] for e in evidence if 'last_path_commit' in e])),
                     'alternative_branch_evidence':alternative_evidence,
                     'integration_call_path_evidence':(['main_graph','main_navigation','main_manifest','main_api'] if location=='main' else ['parallel_graph','parallel_navigation','parallel_manifest','parallel_api','main_graph']),
                     'tests_executed':tests,'inherited_acceptance_receipts':log_receipts,
                     'receipt_blob_continuity':continuity,
                     'tests_still_required':[] if verdict=='A' else [task['automated_tests'],task['physical_device'],missing],
                     'external_blockers':[] if external is None else [external],
                     'confidence':'high' if verdict=='A' else 'medium',
                     'confidence_limit':'Source-level finding; no unperformed acceptance inferred.' if verdict!='A' else 'Bounded applicable acceptance only; subsequent feature gates remain separate.',
                     'old_closure_accurate':old_accurate,'old_status_assessment':accuracy,
                     'reporting_verdict':verdict})
    dump(EVIDENCE_CACHE_PATH,{location+'|'+token:value for (token,location),value in EVIDENCE_CACHE.items()})
    assert not unresolved, '\n'.join(unresolved)
    dirty_evidence = {(e['location'],e['path']) for r in rows for e in r['evidence'] if not e.get('worktree_matches_ref', True)}
    assert dirty_evidence <= {('parallel','android/app/build.gradle.kts')}, f'Dirty evidence must be disclosed explicitly: {dirty_evidence}'
    metrics = {
        'total':len(rows),
        'fully_implemented_any_reviewed_branch':sum(r['implementation_status']=='IMPLEMENTED' for r in rows),
        'fully_implemented_main':sum(r['main_implementation_status']=='IMPLEMENTED' for r in rows),
        'integrated_active_application':sum(r['integration_status']=='INTEGRATED' for r in rows),
        'passed_all_required_verification':sum(r['verification_status'] in {'ACCEPTED','TESTS_PASSING'} for r in rows),
        'formally_closed':sum(r['closure_status']=='COMPLETE' for r in rows),
        'fully_implemented_outstanding_verification':sum(r['implementation_status']=='IMPLEMENTED' and r['verification_status'] not in {'ACCEPTED','TESTS_PASSING'} for r in rows),
        'acceptance_only_current_app':sum(r['acceptance_only_in_current_app'] for r in rows),
        'partially_implemented_needing_code':sum(r['implementation_status']=='PARTIALLY_IMPLEMENTED' for r in rows),
        'partial_tasks_needing_production_code':sum(r['implementation_status']=='PARTIALLY_IMPLEMENTED' and r['production_code_remaining'] for r in rows),
        'partial_tasks_needing_only_test_code':sum(r['implementation_status']=='PARTIALLY_IMPLEMENTED' and r['test_code_remaining'] and not r['production_code_remaining'] for r in rows),
        'not_implemented':sum(r['implementation_status']=='NOT_IMPLEMENTED' for r in rows),
        'external_blocked':sum(bool(r['external_blockers']) for r in rows),
        'implemented_only_unmerged':sum(r['exclusive_category']=='implemented_unmerged' for r in rows),
        'any_implementation_or_scaffold_only_unmerged':sum(r['implementation_location']=='parallel' for r in rows),
        'production_code_remaining_including_integration':sum(r['production_code_remaining'] for r in rows),
        'feature_code_remaining':sum(r['feature_code_remaining'] for r in rows),
        'integration_code_remaining':sum(r['integration_code_remaining'] for r in rows),
        'reopened_previous_complete':sum(r['old_status']=='COMPLETE' and r['closure_status']!='COMPLETE' for r in rows),
    }
    exclusive = dict(sorted(Counter(r['exclusive_category'] for r in rows).items()))
    assert sum(exclusive.values()) == 149
    historical = {state:dict(sorted(Counter(r['exclusive_category'] for r in rows if r['old_status']==state).items())) for state in ['COMPLETE','PARTIAL','NOT STARTED','BLOCKED']}
    evidence_logs = ['book-activity-serial-gradle.log','book-activity-serial-backend.log','book-activity-api34.private.log','audit-parallel-configuration.log','audit-push-provider.log']
    wiring={}
    for location in ['main','parallel']:
        for key,path in [('graph','AtharApplication.kt'),('navigation','android/app/src/main/java/org/athar/app/core/ui/MainShell.kt'),('manifest','android/app/src/main/AndroidManifest.xml'),('api','backend/src/Http/ApiKernel.php')]:
            wiring[location+'_'+key]=evidence_file(path,location)
    committed_entry=git(ROOT,'show',TARGET+':public/index.php')
    wiring['main_admin_entry']={'path':'public/index.php','ref':TARGET,'git_blob':git(ROOT,'rev-parse',TARGET+':public/index.php'),
        'read_source':'Committed snapshot; two unrelated pre-existing mail wording lines remain dirty in working tree.',
        'anchors':[{'line':n,'text':line.strip()[:180]} for n,line in enumerate(committed_entry.splitlines(),1) if re.search(r'user-profile-update|managedUserWhere|admin_require_auth|admin_authorize_kind',line)]}
    data = {'schema_version':1,'date':DATE,'main_branch':'codex/android-ecosystem','main_sha':TARGET,
            'parallel_branch':'parallel/admin-foundation','parallel_sha':SOURCE,
            'other_reviewed_branches':{ref:git(ROOT,'rev-parse',ref) for ref in ['origin/chatgpt/athar-premium-ui-v3','origin/preview/athar-ui-showcase','origin/uiux/athar-premium-v2','codex/athar-premium-ui','codex/premium-ui-baseline-20261027','origin/main']},
            'authority':'Source review TSV + exact source/blob/test receipt evidence; summaries are generated. This audit overrides historic progress estimates.',
            'metrics':metrics,'exclusive_partition':exclusive,'historical_status_reclassification':historical,
            'metric_rules':[
                'Implementation means functional source is present in main OR explicitly reviewed parallel branch; it does not mean build, integration or acceptance succeeded.',
                'INTEGRATED means full bounded scope is connected in the active application; partial call paths do not receive integration credit.',
                'All required verification means ACCEPTED/TESTS_PASSING for the entire task, not a passing unit subset. No open native task is promoted by 167 JVM or35 emulator cases.',
                'Unmerged count in headline means fully implemented source only; a separate broader count includes partial code and measurement/release scaffolds.',
                'Production-code workload includes required feature changes AND deliberate integration/compatibility ports. It excludes hypothetical fixes for unrun measurements.',
                'Measurement/release scaffold means machinery exists but the task outcome has not been produced. This is neither zero implementation nor a completed measurement.',
                'Categories in metrics overlap; exclusive_partition assigns each task exactly once and totals149.',
                'Main implementation state for unmerged scopes indicates only an older partial baseline when explicitly present; never imported branch code.',
            ],
            'current_validation':{'inherited_source_sha':TARGET,'jvm_cases':167,'jvm_classes':38,'junit_receipts':junit,
                'backend_suites':24,'backend_assertions':21192,'instrumentation_cases':35,'instrumentation_platform':'API34 emulator; actual Room/Keystore/files/isolated PDF, injected transport',
                'lint_errors':0,'lint_warnings':59,'resources_paired':432,
                'new_checks':['Git fetch and exact refs/status','Parallel Gradle configuration FAILED','Push adapter PHP8.0 execution FAILED before assertions','432 paired resources PASS','Audit/queue/ledger consistency validation','Secret screening:0 working findings;2 historical synthetic credential-URL rejection fixtures manually reviewed, not hidden as a clean scanner exit.'],
                'secret_screening':{'receipt':'artifacts/functional-integration/audit-security.log','scanner_exit':'nonzero:2 history heuristic findings',
                    'working_findings':0,'history_blobs_scanned':2560,'working_files_at_scan':810,
                    'manual_adjudication':[{'object':'a350300ce4ba59982e77d62bf7faf773c468e465','path':'tests/admin_campaign_http_test.py','line':38,'finding':'Synthetic example.invalid userinfo URL is an intentional invalid-image rejection input.'},
                                           {'object':'bfc9d31b570c4b7f683c5a2486370557a8b93f7e','path':'tests/campaign_test.php','line':25,'finding':'Synthetic example.invalid userinfo URL is an intentional invalid-fields rejection input.'}],
                    'limit':'History not rewritten and scanner not weakened. This audit is not final release23.6 clearance.'},
                'logs':[fingerprint(ROOT/'artifacts/functional-integration'/name) for name in evidence_logs],
                'limitations':['No new physical-device/provider/hosted/signing acceptance.','No source-branch Android compile/test pass: configuration fails.','No FCM delivery/real Gmail inbox proof.','Physical historical logs are inherited;7 changed native scopes reopened.','Evidence logs may contain private fixtures and remain ignored; only relative paths/digests and bounded counts are committed.']},
            'preserved_dirty_paths':['public/index.php','scripts/test-admin-recovery.ps1'],
            'parallel_dirty_caveat':'Pre-existing android/app/build.gradle.kts File/import repair preserved; configuration failure is on that working tree, not a clean7392 build.',
            'next_task':{'id':'07.7','action':'Reconcile canonical Khatmah workload/date contract and revisioned shared API/web writer first; then port editor without overwriting drafts. Do not resume until audit commit/checks complete.',
                         'deferred':'02.8 plus reopened hardware acceptance are queued until an authorized physical session; approved provider gates remain explicit.'},
            'wiring_evidence':wiring,'tasks':rows}
    if CHECKPOINT:
        data['original_audit_commit']=BASELINE_AUDIT_COMMIT
        data['original_audit_metrics']=baseline_audit['metrics']
        data['baseline_validation']=baseline_audit['current_validation']
        data['current_validation']=CHECKPOINT['validation']
        data['functional_checkpoint']=CHECKPOINT
        data['next_task']=CHECKPOINT['next_task']
        queue['current_task']=CHECKPOINT['next_task']['id']
    # Checks deliberately fail if someone silently changes a status without reconciling all generated views.
    if args.check:
        assert previous == data, 'Audit JSON stale; regenerate after explicit scope review.'
        assert queue['audit_metrics']==metrics, 'Queue summary metrics stale'
        for row in rows:
            task = next(t for t in queue['tasks'] if t['id']==row['id'])
            for key in ['implementation_status','integration_status','verification_status','closure_status','production_code_remaining']:
                assert task[key] == row[key], (row['id'],key)
            assert task['state'] == row['closure_status'].replace('_',' ')
        ledger = json.loads((PROGRESS/'FUNCTIONAL_INTEGRATION_LEDGER.json').read_text(encoding='utf-8'))
        assert ledger['actual_audit_metrics'] == metrics
        assert len(ledger['tasks']) == 149
        for row, item in zip(rows,ledger['tasks']):
            assert row['id']==item['id']
            for key in ['implementation_status','integration_status','verification_status','closure_status']:
                assert row[key]==item[key],(row['id'],key)
        for name in RECONCILED_DOCS:
            content=(ROOT/name).read_text(encoding='utf-8')
            assert '<!-- actual-implementation-audit:start -->' in content and 'ACTUAL_IMPLEMENTATION_AUDIT.md' in content,name
            current_block=content.split('<!-- actual-implementation-audit:start -->',1)[1].split('<!-- actual-implementation-audit:end -->',1)[0]
            assert f"{metrics['fully_implemented_any_reviewed_branch']} tasks have implemented source ({metrics['fully_implemented_main']} in main, {metrics['implemented_only_unmerged']} only unmerged)" in current_block,name
            assert f"{metrics['formally_closed']} are accepted/closed, {metrics['acceptance_only_current_app']} need current acceptance only" in current_block,name
        parallel=json.loads((PROGRESS/'PARALLEL_INTEGRATION.json').read_text(encoding='utf-8'))
        assert parallel['codex_head']==TARGET and parallel['parallel_head']==SOURCE
        devices=json.loads((PROGRESS/'DEVICE_ACCEPTANCE_QUEUE.json').read_text(encoding='utf-8'))
        reopened=next(s for s in devices['sessions'] if s['id']=='audit-reopened-native-foundations')
        assert set(reopened['tasks'])=={r['id'] for r in rows if r['old_status']=='COMPLETE' and r['closure_status']!='COMPLETE'}
        assert (PROGRESS/'ACTUAL_IMPLEMENTATION_AUDIT.md').read_text(encoding='utf-8') == report_text(data),'Audit Markdown stale'
        print('PASS:149 explicit reviews; source evidence/ref/blob continuity; exact generated metrics; queue/ledger consistency.')
        return
    dump(AUDIT,data)
    for task, row in zip(queue['tasks'],rows):
        for key in ['implementation_status','integration_status','verification_status','closure_status','production_code_remaining']:
            task[key] = row[key]
        task['state'] = row['closure_status'].replace('_',' ')
        task['missing'] = row['missing_requirements']
        task['audit_evidence'] = [{'location':e['location'],'path':e['path'],'ref':e['ref']} for e in row['evidence']]
        task['audit_old_status'] = row['old_status']
    queue['actual_audit'] = 'ACTUAL_IMPLEMENTATION_AUDIT.json'
    queue['audit_metrics'] = metrics
    queue['status_meaning'] = 'Four independent axes and concrete missing/evidence fields are authoritative. Historical51.38 estimate is frozen/obsolete; never use it as actual engineering completion. See ACTUAL_IMPLEMENTATION_AUDIT.md.'
    queue['active_checkpoint'] = '149-task source findings reconciled; exact current checkpoint/validation in ACTUAL_IMPLEMENTATION_AUDIT.json. Next feature'+queue['current_task']+'. Original audit retained as dated evidence.'
    queue['overall_estimate_status'] = 'OBSOLETE subjective historic estimate; actual count metrics are generated from the149 audited records.'
    dump(queue_path,queue)
    ledger_path = PROGRESS/'FUNCTIONAL_INTEGRATION_LEDGER.json'
    ledger = json.loads(ledger_path.read_text(encoding='utf-8'))
    ledger['actual_audit'] = 'ACTUAL_IMPLEMENTATION_AUDIT.json'
    ledger['actual_audit_metrics'] = metrics
    ledger['audit_target_sha'] = TARGET
    ledger['audit_source_sha'] = SOURCE
    ledger['historic_inventory_notice'] = 'Earlier candidate lists and cursor are historical source inventory, not task implementation verdicts. Four-axis actual audit supersedes codex_state/review_state interpretations.'
    by_id = {r['id']:r for r in rows}
    for item in ledger['tasks']:
        row=by_id[item['id']]
        for key in ['implementation_status','integration_status','verification_status','closure_status','production_code_remaining']:
            item[key]=row[key]
        item['codex_state']=row['closure_status'].replace('_',' ')
        item['review_state']='ACTUAL SOURCE AUDITED; '+row['exclusive_category']
        item['missing']=row['missing_requirements']
        item['actual_audit_record']=row['id']
        item['actual_evidence']=[{key:e[key] for key in ['location','path','ref','git_blob'] if key in e} for e in row['evidence']]
        item['historic_parallel_state_claim']=item.get('historic_parallel_state_claim',item.get('parallel_state'))
        item['parallel_state']='SEE ACTUAL AUDIT: '+(row['implementation_status'] if row['implementation_location']=='parallel' else 'HISTORIC SOURCE CLAIM NOT USED FOR CURRENT VERDICT')
    for wp, item in ledger['work_packages'].items():
        subset=[r for r in rows if str(int(r['id'].split('.')[0]))==wp]
        if subset: item['actual_closure_counts']=dict(Counter(r['closure_status'] for r in subset))
    dump(ledger_path,ledger)
    (PROGRESS/'ACTUAL_IMPLEMENTATION_AUDIT.md').write_text(report_text(data),encoding='utf-8')
    reconcile_documents(data)
    print(json.dumps({'metrics':metrics,'exclusive_partition':exclusive,'historical':historical},ensure_ascii=False,indent=2))

def report_text(data):
    rows=data['tasks']; metrics=data['metrics']
    lines=['# Actual implementation audit —149 tasks','',f"Audit date: {DATE}. Active branch `{data['main_branch']}` at `{TARGET}`. Parallel `{data['parallel_branch']}` at `{SOURCE}`.",'',
           'This report supersedes the26 COMPLETE /77 PARTIAL /42 NOT STARTED /4 BLOCKED snapshot as an implementation inventory. Historical receipts remain dated records. Original audit9492f14 changed no application code. Later functional checkpoints are separately identified; changed task findings advance only with explicit source review and focused evidence. Seven previously closed native scopes remain reopened because changed call paths have not received the required current hardware acceptance.','',
           '## Generated metrics','', '| Metric | Tasks |','|---|---:|']
    lines += [f'| {key} | {value} |' for key,value in metrics.items()]
    lines += ['', 'Counts overlap. Fully implemented includes unmerged reviewed source and can have failing verification; main-only count is separate. Integrated excludes partial call paths. All required verification is a whole-task gate, not passing unit tests. Production code remaining includes integration ports; feature coding is separately counted. No weighted percentage is claimed.','',
              '## Mutually exclusive partition','', '| Category | Tasks |','|---|---:|']
    lines += [f'| {key} | {value} |' for key,value in data['exclusive_partition'].items()]
    lines += ['| **Total** | **149** |','', '## Historical state reconciliation','', '| Old state | Actual categories |','|---|---|']
    lines += [f"| {state} | "+'; '.join(f'{key}: {value}' for key,value in counts.items())+' |' for state,counts in data['historical_status_reclassification'].items()]
    lines += ['', '## What the last three days delivered','',
              'The Git sequence records source reconciliation, rather than an unchanged implementation: WP03 canonical navigation/search/share/tafsir/daily restoration; WP04 owned Media3 playback/focus/recovery/timing/radio; WP05 full discovery/NULL-repeat/daily counters/genuine statistics; WP06 rights/validated durable downloads/isolated PDF; WP07 validated profile/security/private export/durable deletion/saved collection paging/genuine completed-download activity. These were pushed as separate reviewed increments through2b7d7de. The number26 was repeatedly retained as strict historical closure while finished coding grew; it was not a coding-completion count. This audit also reduces closure where source continuity no longer supports current hardware acceptance.','',
              'Representative feature commits:1c922ba,48d4c73,f6ccdf8,a7d162f,7cdea16,5fb3cdd,5290bfc,09b2982,b7e1821,90bc65e,e5d9f1c,a0e8fdb,d6c63a8,2b7d7de. Full per-file commits/blob evidence are in the JSON. A file last-change commit is provenance, not proof that the entire feature was accepted.','',
              '## Source boundaries and missing production work','',
              'Khatmah remains genuine coding work: revisionless web writes, finished-plan update bypass, overdue start/date validation, rounding and concurrent ACK/draft retention require reconciliation. Main Night Prayer history note edits reconstruct a subset of fields; the parallel editor already preserves measured duration/context and needs deliberate integration. Native hadith detail/search awaits an approved provider contract. CampaignPushDispatcher is absent from the canonical main path; push/combined readiness is correctly false. Parallel device push acquisition defaults to NoopDevicePushTokenSource returning null and lacks the real incoming provider service. Reminder eligibility/posting is not atomic with ledger recording: two concurrent Workers can both pass old global frequency/dedupe state before either records delivery.10.4/14.3 therefore require code, not just acceptance.','',
              'Many previously NOT STARTED modules have substantial parallel implementations: timer/year calendar, optional model delivery/local inference/alignment, preference/channels/deep-links/local scheduling, lesson reminders/follows/routines and inbox. They are not integrated into the active AppGraph/manifest/current Room/deletion/session contracts. Parallel Android configuration fails. PHP8.0.30 also rejects parallel readonly property syntax before the injected push test reaches assertions. Implemented source therefore receives no build, runtime, provider or integration credit.','',
              'Measurement/release tasks have real tooling but no completed outcome: representative review accuracy/latency, startup, idle battery/network, signed reproducibility, hosted acceptance and handoff. Scaffolding is recorded separately; no99% accuracy, release startup number, battery result or delivered signed artifact is fabricated.','',
              'UI-v3 contains actual native changes and compile fixes: PCM constant, model Worker API/coroutine ownership, File resolution and a typed generated-assets task wired through androidComponents Variant API. The latter is an existing candidate correction for the inspected SourceSet Provider failure; do not rewrite it blindly. The failed configuration was on7392 with its local import repair; no failed or passed build is claimed for UI-v3. It also contains a debug showcase; preview is not production acceptance. origin/uiux/athar-premium-v2 is a22-file design/presentation delta. Branches were compared against parallel rather than counted wholesale as integrated functionality. Current native Canvas artwork/hero primitives already exist, so21.19 is not NOT STARTED; per-section presentation/acceptance remains separate.','',
              '## Verification receipts and limits','',
              'Baseline receipts at source2b7d7de:167 JVM cases/38 classes, debug and test APK builds, lint0 errors/59 warnings,24 backend suites/21192 executed assertions,35 actual API34 instrument cases in15.109s. Reuse applies only to unchanged paths, not newly integrated code. The nested15 media assertions inside a combined31 banner are not counted twice. API34 exercises actual Room/Keystore/files/installed isolated PDF with injected transports; it does not prove native real HTTP, OS process/reboot, physical device or real provider delivery. Original38 XML fingerprints remain frozen in original audit9492f14 and baseline_validation, even when targeted later runs replace local XML files.','',
              'Newly executed audit checks: Git fetch/ref/dirty checks;432 paired AR/EN resources PASS; parallel Gradle configuration FAILED at SourceSet Provider API; injected server push adapter test FAILED at PHP8.0 readonly syntax before assertions. Secret screening found0 working findings and2 historical heuristic findings; both inspected blobs contain intentional example.invalid credential-URL rejection fixtures. The scanner exited nonzero; this is recorded as adjudicated synthetic inputs, not an unexplained clean PASS. History/scanner were not changed. No production fix was made during this audit. The pre-existing parallel File/import repair remains dirty and disclosed. Main current passing suites were not unnecessarily rerun. Documentation consistency checks run after reconciliation.','',
              'Retained closures are backed by implementation review and available inherited receipts. Admin authentication/action/user/privacy219 HTTP is a regression receipt, not a new audit run; WP18 combined118 HTTP/142 browser and WP20 final42 HTTP/worker/15 browser remain bounded local proofs. No real message is inferred. Original API27 foundation receipts are retained as history; seven changed native scopes are explicitly reopened for current hardware acceptance.','',
              '## Reopened previous closures','']
    for r in rows:
        if r['old_status']=='COMPLETE' and r['closure_status']!='COMPLETE':
            lines.append(f"- **{r['id']} {r['name']}**: {r['exact_remaining_work'][0]}")
    lines += ['', '## Genuine external gates','']
    for r in rows:
        if r['external_blockers']: lines.append(f"- **{r['id']}**: {r['external_blockers'][0]}. {r['exact_remaining_work'][0]}")
    lines += ['', '## Documentation corrections and execution order','',
              'TASK_QUEUE.json/MD and FUNCTIONAL_INTEGRATION_LEDGER.json now carry IMPLEMENTATION / INTEGRATION / VERIFICATION / CLOSURE / MISSING / EVIDENCE for every task. The explicit review TSV records individual scope findings; this JSON is the generated authoritative evidence/metrics view. ACCOUNT_FUNCTIONAL_INTEGRATION and current progress/testing headers point here. Historical percentages/counts remain labeled historical and cannot override this audit. The renderer/validator rejects missing axes and inconsistent totals.','',
              f"Next executable task: **{data['next_task']['id']}**. {data['next_task']['action']} Integrate08.x without regressing current conflict/rejection recovery; reconcile runtime/build/PHP contracts before09–16, then canonical push campaigns/combined mail. Batch authorized physical acceptance for finished native tasks and reopened foundations. Keep03.8/15.3/20.5/24.1 external gates explicit; final security/performance/release gates follow stable integration.",'',
              '## Full task reclassification table','',
              'I: IMPLEMENTED / PARTIALLY_IMPLEMENTED / SCAFFOLDED / IMPLEMENTATION_BLOCKED. Integration and verification are separate columns; PARTIAL closure never implies missing feature code by itself. Every row has concrete present/missing/source details below and in JSON.','',
              '| ID | Old | Implementation | Integration | Verification | Closure | Evidence |','|---|---|---|---|---|---|---|']
    for r in rows:
        ev='; '.join(f"{e['location']}:{e['path']}" for e in r['evidence'])
        lines.append(f"| {r['id']} | {r['old_status']} | {r['implementation_status']} | {r['integration_status']} | {r['verification_status']} | {r['closure_status']} | {ev} |")
    lines += ['', '## Individual scope findings','']
    for r in rows:
        lines += [f"### {r['id']} — {r['name']}",'',f"IMPLEMENTATION: `{r['implementation_status']}` ({r['implementation_location']}); "+r['implemented_requirements'][0],
                  f"INTEGRATION: `{r['integration_status']}`. VERIFICATION: `{r['verification_status']}`. CLOSURE: `{r['closure_status']}`.",
                  'MISSING: '+('; '.join(r['missing_requirements']) or 'None in this bounded accepted scope.'),
                  'EVIDENCE: '+ '; '.join(f"`{e['location']}:{e['path']}`" for e in r['evidence'])+'. Exact refs, symbols/lines, blobs, commits, inherited tests, still-required tests and confidence are in the JSON record.',
                  'Old status: '+r['old_status_assessment'],'']
    if data.get('functional_checkpoint'):
        cp=data['functional_checkpoint']
        v=cp['validation']
        lines[4:4]=['## Functional update after the original audit','',cp['summary'],'',
                    f"Current focused validation, separate from the inherited baseline: **{v['jvm_cases']} JVM / {v['backend_assertions']} backend assertions / {v['actual_cookie_api_http_assertions']} actual cookie/API HTTP assertions**. Debug/test APK PASS; lint{v['lint_errors']} errors/{v['lint_warnings']} warnings;{v['resources_paired']} paired resources. {v.get('instrumentation_cases_executed', 0)} scoped emulator cases executed; physical acceptance remains pending. Exact component counts, source SHA and receipt digests are in ACTUAL_IMPLEMENTATION_AUDIT_INPUT.json.",'']
    return '\n'.join(lines)

def reconcile_documents(data):
    metrics=data['metrics']
    marker_start='<!-- actual-implementation-audit:start -->'
    marker_end='<!-- actual-implementation-audit:end -->'
    for name in RECONCILED_DOCS:
        path=ROOT/name
        source=path.read_text(encoding='utf-8')
        source=re.sub(re.escape(marker_start)+r'.*?'+re.escape(marker_end)+r'\n*','',source,flags=re.S)
        link='ACTUAL_IMPLEMENTATION_AUDIT.md' if '/progress/' in name else '../progress/ACTUAL_IMPLEMENTATION_AUDIT.md'
        note=f"{marker_start}\n> **Current authority —{DATE}:** [{metrics['total']}-task actual source audit]({link}). {metrics['fully_implemented_any_reviewed_branch']} tasks have implemented source ({metrics['fully_implemented_main']} in main, {metrics['implemented_only_unmerged']} only unmerged); {metrics['formally_closed']} are accepted/closed, {metrics['acceptance_only_current_app']} need current acceptance only. Seven earlier native closures reopened after source changes. These metrics overlap; use the audit's exclusive149 partition. Older counts, percentages, next-task statements and dated test results below are historical receipts, not current inventory or newly executed tests.\n\n"
        if name.endswith('ACCOUNT_FUNCTIONAL_INTEGRATION.md'):
            note+='> **WP07.1–07.6:** IMPLEMENTATION: IMPLEMENTED. INTEGRATION: INTEGRATED. VERIFICATION: ACCEPTANCE_PENDING. CLOSURE: PARTIAL. MISSING: real native HTTP/OS/physical/chooser acceptance appropriate to each task. EVIDENCE:2b7d7de baseline and inherited167 JVM/24 backend/35 API34 gates; full per-task records in the audit. '
            account_checkpoint=next((cp for cp in reversed(TASK_CHECKPOINTS) if any(t.startswith('07.') for t in cp.get('reviewed_tasks',[]))),None)
            note+=(account_checkpoint['summary']+' See KHATMAH_FUNCTIONAL_INTEGRATION.md and its focused receipts.' if account_checkpoint else '07.7–07.9 still require Khatmah contract/conflict code and integration.')+'\n\n'
        note+=marker_end+'\n\n'
        first, separator, rest=source.partition('\n')
        path.write_text(first+'\n\n'+note+rest.lstrip('\n') if separator else note+source,encoding='utf-8')
    path=PROGRESS/'PARALLEL_INTEGRATION.json'
    d=json.loads(path.read_text(encoding='utf-8'))
    if 'previous_feature_checkpoint' not in d: d['previous_feature_checkpoint']=d.get('active_review')
    if 'historic_codex_head' not in d: d['historic_codex_head']=d.get('codex_head')
    d['codex_head']=TARGET
    d['parallel_head']=SOURCE
    d['review_worktree_checkout']=SOURCE
    d['last_fetch_date']=DATE
    d['progress_audit']='ACTUAL_IMPLEMENTATION_AUDIT.json is authoritative; historic26/77/42/4 and51.38 estimate superseded. Contiguous infrastructure cursor is historical, not a claim that all later feature source is unreviewed.'
    d['active_review']={'tasks':CHECKPOINT.get('reviewed_tasks',[r['id'] for r in data['tasks']]),'state':'SOURCE RECONCILED; CURRENT FOUR-AXIS FINDINGS AND FOCUSED EVIDENCE IN ACTUAL AUDIT',
                        'source_head':SOURCE,'target_baseline':TARGET,'report':'ACTUAL_IMPLEMENTATION_AUDIT.json','next_feature_task':data['next_task']['id']}
    dump(path,d)
    path=PROGRESS/'PREMIUM_UI_MILESTONE.json'
    d=json.loads(path.read_text(encoding='utf-8'))
    d['actual_audit']='ACTUAL_IMPLEMENTATION_AUDIT.json'
    d['historic_snapshot_notice']=f'paused_execution counts/base commit are dated planning history. Runtime source/current acceptance and all task counts are superseded by{DATE} actual audit; no new user visual acceptance is inferred.'
    dump(path,d)
    path=PROGRESS/'DEVICE_ACCEPTANCE_QUEUE.json'
    d=json.loads(path.read_text(encoding='utf-8'))
    d['updated']=DATE
    d['actual_audit']='ACTUAL_IMPLEMENTATION_AUDIT.json'
    reopened=[r['id'] for r in data['tasks'] if r['old_status']=='COMPLETE' and r['closure_status']!='COMPLETE']
    item={'id':'audit-reopened-native-foundations','state':'QUEUED','tasks':reopened,
          'device':'Authorized API27 ARM32 physical target; later permission flows also need appropriate API33+ target',
          'scenarios':[r['exact_remaining_work'][0] for r in data['tasks'] if r['id'] in reopened],
          'existing_evidence':'Dated hardware receipts remain historical. Current local unit/storage subset is passing but does not close changed native HTTP/physical journeys.'}
    d['sessions']=[s for s in d['sessions'] if s['id']!=item['id']]+[item]
    dump(path,d)

if __name__=='__main__': main()

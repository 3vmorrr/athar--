"""Validate and render the ordered implementation queue without changing task states."""
import json
import sys
from pathlib import Path

root=Path(__file__).resolve().parents[1]
source=root/'docs/progress/TASK_QUEUE.json'
data=json.loads(source.read_text(encoding='utf-8'))
tasks=data['tasks'];ids={row['id'] for row in tasks}
assert len(ids)==len(tasks),'Duplicate task ID'
required={'id','name','parent','scope','state','dependencies','modules','automated_tests','physical_device','definition_of_done','estimated_contribution_pp','implementation_status','integration_status','verification_status','closure_status','missing','audit_evidence'}
for row in tasks:
    assert required<=row.keys(),row['id']
    assert row['state'] in {'NOT STARTED','PARTIAL','BLOCKED','COMPLETE'},row['id']
    assert row['implementation_status'] in {'NOT_IMPLEMENTED','SCAFFOLDED','PARTIALLY_IMPLEMENTED','IMPLEMENTED','IMPLEMENTATION_BLOCKED'},row['id']
    assert row['integration_status'] in {'NOT_INTEGRATED','PARTIALLY_INTEGRATED','INTEGRATED','INTEGRATION_BLOCKED'},row['id']
    assert row['verification_status'] in {'NOT_TESTED','TESTS_FAILING','TESTS_PASSING','ACCEPTANCE_PENDING','ACCEPTED','VERIFICATION_BLOCKED'},row['id']
    assert row['closure_status'].replace('_',' ')==row['state'],row['id']
    assert row['audit_evidence'],row['id']
    assert row['state']=='COMPLETE' or row['missing'],f"Unexplained open task {row['id']}"
    if row['state']=='COMPLETE':
        assert row['implementation_status']=='IMPLEMENTED' and row['integration_status']=='INTEGRATED' and row['verification_status']=='ACCEPTED',row['id']
    assert all(dep in ids for dep in row['dependencies']),row['id']
    assert row['definition_of_done'] and row['scope'] and row['automated_tests']
positions={row['id']:index for index,row in enumerate(tasks)}
for row in tasks:
    assert all(positions[dep]<positions[row['id']] for dep in row['dependencies']),f"Dependency must precede {row['id']}"
assert data['current_task'] in ids
assert sum(data['phase2_weights'].values())==100
open_tasks=[row for row in tasks if row['state']!='COMPLETE']
lines=['# Ordered execution queue','',
       'The [actual149-task audit](ACTUAL_IMPLEMENTATION_AUDIT.md) is authoritative. Source reconciliation is complete at the reported snapshot; next feature07.7 follows the audit checkpoint report. Four axes below distinguish finished code from integration and acceptance.','',data['policy'],'',
       f"Snapshot: {data['updated']}. Next feature task after audit: **{data['current_task']}**. Open integration/acceptance tasks: **{len(open_tasks)}**. Historical weighted estimates are obsolete; use the generated audit counts.",'',
       data.get('status_meaning', ''),'',
       'The JSON beside this file is the editable source. Run `python scripts/render-task-queue.py` after an update. COMPLETE requires the recorded applicable gates, docs, reviewed commit and verified normal push. Commit receipts live in TASK_COMPLETIONS.md to avoid self-referential hashes. Dependencies reference queue IDs; blocked external gates may be skipped only for an independently ready task, with the skip reported. The parallel branch leads implementation; this track accepts one integration task at a time under PARALLEL_INTEGRATION.json.','',
       'Contribution values and Phase2 weights below are historical planning metadata, not measured progress or earned credit. Never use them to derive implementation completion.','']
for row in tasks:
    lines.extend([f"## {row['id']} — {row['name']}",'',
        f"- Parent: {row['parent']}; state: **{row['state']}**; dependencies: {', '.join(row['dependencies']) or 'existing approved foundation'}.",
        f"- IMPLEMENTATION: **{row['implementation_status']}**.",
        f"- INTEGRATION: **{row['integration_status']}**.",
        f"- VERIFICATION: **{row['verification_status']}**.",
        f"- CLOSURE: **{row['closure_status']}**.",
        f"- MISSING: {'; '.join(row['missing']) or 'None within the bounded accepted scope.'}",
        f"- EVIDENCE: {'; '.join(e['location']+':'+e['path']+' @ '+e['ref'][:12] for e in row['audit_evidence'])}. Per-task symbols, blobs, commits and test limits: ACTUAL_IMPLEMENTATION_AUDIT.json.",
        f"- Exact scope: {row['scope']}",
        f"- Files/modules: {'; '.join(row['modules'])}.",
        f"- Automated gates: {row['automated_tests']}",
        f"- Physical-device gate: {row['physical_device']}",
        f"- Definition of Done: {row['definition_of_done']}",
        f"- Estimated contribution: {row['estimated_contribution_pp']:.2f} overall percentage points.",''])
rendered='\n'.join(lines)
override=data.get('execution_override')
if override and override.get('active'):
    rendered = '\n'.join([
        '# Active priority: Athar Premium Android UI', '',
        'The user has paused unrelated backend/integration execution. The original ordered queue below is retained as history and acceptance scope, not the active execution order.', '',
        f"Paused task: **{data['current_task']}**. Active work packages and visual-task dispositions: [premium UI milestone](PREMIUM_UI_MILESTONE.json).", '',
        'The [Athar Premium Design System](../design/ATHAR_PREMIUM.md) supersedes obsolete visual specifications. Reuse completed functionality; do not mark superseded visuals complete or resume backend work before the user reviews the running app.', '',
        '---', '', rendered,
    ])
if '--check' in sys.argv:
    assert source.with_suffix('.md').read_text(encoding='utf-8')==rendered,'Queue Markdown is stale; render it again.'
else:
    source.with_suffix('.md').write_text(rendered,encoding='utf-8')
print(f"PASS: {len(tasks)} unique tasks; {len(open_tasks)} open; current {data['current_task']}.")

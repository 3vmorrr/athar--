"""Inventory branch/task evidence. Never promote task acceptance automatically."""
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True, encoding='utf-8').strip()


def build():
    source = git('rev-parse', 'origin/parallel/admin-foundation')
    target = git('rev-parse', 'HEAD')
    queue = json.loads((ROOT / 'docs/progress/TASK_QUEUE.json').read_text(encoding='utf-8'))
    source_queue = json.loads(git('show', source + ':docs/progress/TASK_QUEUE.json'))
    source_tasks = {t['id']: t for t in source_queue['tasks']}
    changes = [line.split('\t') for line in git('diff', '--name-status', target, source).splitlines()]
    history = {}
    tasks = []
    for task in queue['tasks']:
        modules = tuple(task['modules'])
        if modules not in history:
            log = git('log', '--format=%H%x09%s', target + '..' + source, '--', *modules)
            history[modules] = [dict(zip(('sha', 'subject'), line.split('\t', 1)))
                                for line in log.splitlines() if '\t' in line]
        files = [v[-1] for v in changes if any(v[-1] == p or v[-1].startswith(p.rstrip('/') + '/')
                                              for p in modules)]
        classification = ('EXTERNAL BLOCKER' if task['state'] == 'BLOCKED' else
                          'BOTH MUST BE RECONCILED' if files else 'IMPLEMENTATION EXISTS BUT NOT TESTED')
        tasks.append({
            'id': task['id'], 'name': task['name'], 'work_package': task['parent'],
            'classification': classification,
            'review_state': 'PRELIMINARY INVENTORY; SEMANTIC REVIEW REQUIRED',
            'codex_state': task['state'], 'parallel_state': source_tasks[task['id']]['state'],
            'parallel_candidate_commits': history[modules],
            'candidate_scope': 'Module-history candidates; shared paths do not prove task-specific implementation.',
            'codex_current_implementation': modules, 'files_touched': files,
            'schema_changes': [p for p in files if 'migration' in p.lower() or '/schemas/' in p],
            'api_changes': [p for p in files if p.startswith(('backend/', 'app/', 'public/index.php'))],
            'android_changes': [p for p in files if p.startswith('android/')],
            'admin_changes': [p for p in files if p.startswith(('admin/', 'app/Views/', 'public/assets/'))],
            'background_workers': [p for p in files if re.search(r'worker|scheduler|outbox|dispatch', p, re.I)],
            'dependencies': task['dependencies'],
            'conflict_risk': 'Shared contracts diverged; semantic review required before transfer.',
            'tests_required': task['automated_tests'],
            'device_acceptance_required': task['physical_device'],
            'external_blockers': task.get('blocker', task['scope'] if task['state'] == 'BLOCKED' else None),
            'remaining_acceptance': task['definition_of_done'], 'acceptance_evidence': [],
        })
    ledger = {
        'date': '2026-10-28', 'source_sha': source, 'target_checkpoint_sha': target,
        'remote_target_at_fetch': git('rev-parse', 'origin/codex/android-ecosystem'),
        'merge_base': git('merge-base', target, source),
        'parallel_worktree': 'C:/xampp/athar-parallel',
        'policy': 'Functional integration first. Preliminary inventory does not imply semantic review or acceptance. Preserve current UI; defer cosmetic design. No whole branch merge.',
        'work_packages': {
            str(i): {'state': 'ACCEPTANCE OPEN' if i > 1 else 'HISTORICAL FOUNDATION; REVALIDATE REGRESSION',
                     'task_ids': [t['id'] for t in tasks if re.search(r'package\s*' + str(i) + r'(?:\D|$)', t['work_package'])]}
            for i in range(1, 25)
        },
        'tasks': tasks,
    }
    output = ROOT / 'docs/progress/FUNCTIONAL_INTEGRATION_LEDGER.json'
    if output.exists():
        raise SystemExit('Ledger exists; update reviewed records in place rather than overwriting evidence.')
    output.write_text(json.dumps(ledger, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Inventoried {len(tasks)} tasks across 24 work packages; no acceptance promoted.')


if __name__ == '__main__':
    build()

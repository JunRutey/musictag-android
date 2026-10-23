import json
import urllib.request

UA = {"User-Agent": "dsh-m0-probe", "Accept": "application/vnd.github+json"}


def get(url, follow=True):
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None
    opener = urllib.request.build_opener() if follow else urllib.request.build_opener(NoRedirect)
    req = urllib.request.Request(url, headers=UA)
    return opener.open(req, timeout=45)


try:
    runs = json.load(get("https://api.github.com/repos/actions/checkout/actions/runs?per_page=1"))["workflow_runs"]
    run = runs[0]
    print("run:", run["id"], run["name"], run["status"], run["conclusion"])
    jobs = json.load(get(run["jobs_url"]))["jobs"]
    job = jobs[0]
    print("job:", job["id"], job["name"], job["conclusion"])
    ann_url = f"https://api.github.com/repos/actions/checkout/check-runs/{job['id']}/annotations"
    try:
        ann = json.load(get(ann_url))
        print("annotations:", len(ann), "(check-runs API reachable)")
    except Exception as exc:
        print("annotations failed:", type(exc).__name__, exc)
    try:
        resp = get(f"https://api.github.com/repos/actions/checkout/actions/runs/{run['id']}/logs")
        data = resp.read()
        print("log zip bytes:", len(data), "magic:", data[:2])
    except Exception as exc:
        print("log download failed:", type(exc).__name__, exc)
except Exception as exc:
    print("probe failed:", type(exc).__name__, exc)

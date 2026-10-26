# Free hosting research — 2026-10-24
| Candidate | PHP/MySQL/HTTPS | Native API | Gmail SMTP / outbound | Storage/model | Decision |
|---|---|---|---|---|---|
| Local XAMPP + LAN HTTPS | Available locally; old runtime | Technically possible on LAN | Local SMTP authentication passed earlier; message delivery not verified | Local disk | Development only; never a public production claim |
| InfinityFree free tier | Advertised PHP/MySQL/SSL | **Incompatible**: mandatory JavaScript/cookie challenge blocks mobile apps | Must not assume provider account support | File/request limits require checks | Reject for shared Android API |
| alwaysdata free | Provider offers a limited free plan; PHP/MySQL/SSL documented | No deployed account/API probe yet | Hosted Gmail egress not verified | 100 MB advertised free storage; model would consume most | Candidate only if binaries are separately distributed and real deployment tests pass |

Sources: [InfinityFree browser security](https://forum.infinityfree.com/t/browser-security-system-features-and-limitations/49353), [alwaysdata offers](https://www.alwaysdata.com/en/offers/), [free plan terms](https://www.alwaysdata.com/en/contract/5/). Published offers are not runtime evidence. Cron, outbound HTTP/SMTP, quotas, retention and terms must be checked on an actual free account before selection. Do not bypass a host's client restrictions.

No mandatory paid SDK/API/database/inference. Optional domain/store-distribution charges are not zero; sideloaded debug APKs avoid a store account during development. A supported PHP host can later replace a free provider using environment configuration and the same API/database contract.

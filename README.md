# Jira Portal

Személyes Jira-dashboard és worklog monitor a B+N Referencia Jira Data Centeréhez.

- **Backend:** Java 25 + Spring Boot 4 (Spring Security, RestClient)
- **Frontend:** React 19 + TypeScript + Vite + Tailwind CSS v4 + TanStack Query + React Router
- **Jira:** ScriptRunner REST endpointok (`jira-scripts/portalEndpoints.groovy`)

Belépés a saját Jira-felhasználóval. Mindenki a saját adatait látja, a `jira-administrators` tagjai a Worklog monitorban más felhasználókat is megnézhetnek.

## Gyors indítás (saját gépen, Windows)

**Kell hozzá:**

- **JDK 25** vagy újabb, Java a PATH-ban (`java -version`);
- **Node.js 20+** (`npm -v`);
- **Git**;
- céges hálózat vagy VPN, hogy a Jira (`servicedesk.bnref.hu`) elérhető legyen.

A Maven telepítése nem kell, a `mvnw.cmd` letölti.

1. Klónozd a repót egy **helyi** mappába (ne hálózati meghajtóra, például ne a H:-ra):
   ```powershell
   cd "$env:USERPROFILE"
   mkdir dev -Force; cd dev
   git clone https://github.com/<felhasznalonev>/jira-portal.git
   cd jira-portal
   ```
2. A **Belépés Jirával** gombhoz másold le a `backend\config\application.yml.example` fájlt `application.yml` néven ugyanabba a mappába, és írd bele a Client ID-t és a Client secretet. Ezeket a projekt gazdájától kérd el, ne a repóból: a fájl szándékosan nincs verziókezelve. Enélkül csak a jelszavas belépés működik.
3. Indítás:
   ```powershell
   .\start-dev.cmd
   ```
   Két ablak nyílik (backend és frontend). Első alkalommal pár percig tart a Maven és az npm csomagok letöltése.
4. Nyisd meg a böngészőben: **http://localhost:5173**

A `.\start-dev.cmd mock` paranccsal Jira nélkül, szimulált adatokkal indul. Ilyenkor bármilyen felhasználónév jó, a jelszó `demo`.

A ScriptRunner végpontok (`jira-scripts/portalEndpoints.groovy`) a Jirában már telepítve vannak, ezzel a kollégáknak nincs teendőjük.

## Mappaszerkezet

```
jira-portal/
├─ jira-scripts/portalEndpoints.groovy   ScriptRunner végpontok: portalMe, portalMyIssues, portalWorklogs
├─ backend/                              Spring Boot alkalmazás (mvnw.cmd: Maven wrapper)
│  └─ src/main/java/hu/bplusn/portal/
│     ├─ auth/        belépés, munkamenet, rate limit
│     ├─ config/      beállítások, Spring Security, SPA útvonalak
│     ├─ jira/        JiraGateway: HttpJiraGateway (valódi) + MockJiraGateway (mock profil)
│     ├─ dashboard/   /api/dashboard/issues
│     └─ worklog/     számítások (munkanap-átlag, havi keret, 12 hónap), /api/worklog/*
├─ frontend/                             React alkalmazás
│  └─ src/pages/      LoginPage, DashboardPage, WorklogPage
└─ start-dev.cmd                         fejlesztői indítás két ablakban
```

## 1. Kipróbálás Jira nélkül (mock mód)

Szükséges: JDK 25 vagy újabb, Node.js 20+. A Maven telepítése nem kell, a `mvnw.cmd` első futáskor letölti.

```bat
start-dev.cmd mock
```

Ez két ablakot nyit:

- **backend:** `backend\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=mock` → http://localhost:8080
- **frontend:** `npm install`, majd `npm run dev` → **http://localhost:5173** (ezt nyisd meg)

Belépés mock módban:

- bármilyen felhasználónév, jelszó: `demo`;
- az `admin` felhasználó adminisztrátor, vele a Worklog monitorban más személyek is választhatók.

A frontend kódváltozásai azonnal megjelennek a böngészőben (hot reload). A backend módosítása után a backend ablakban `Ctrl+C`, majd indítsd újra.

## 2. ScriptRunner végpontok telepítése

1. Jira → Adminisztráció → **ScriptRunner → REST Endpoints → Create REST Endpoint → Custom endpoint**.
2. Másold be a `jira-scripts/portalEndpoints.groovy` teljes tartalmát (vagy tedd a script rootba, és add meg fájlként).
3. Mentés után három végpont jön létre. Böngészőben, a Jirába belépve ellenőrizhetők:
   - `https://servicedesk.bnref.hu/rest/scriptrunner/latest/custom/portalMe`
   - `https://servicedesk.bnref.hu/rest/scriptrunner/latest/custom/portalMyIssues`
   - `https://servicedesk.bnref.hu/rest/scriptrunner/latest/custom/portalWorklogs?days=7`

A régi `worklogReport` végponthoz nem kell nyúlni, az a Python alkalmazásnak tovább működik.

Fontos különbségek a régi végponthoz képest:

- **Nincs `groups` korlátozás**, mert mindenkinek a saját adatát kell látnia. Anonim hívást mindhárom végpont 401-gyel utasít el, és a más felhasználóra szóló worklog-lekérést csak `jira-administrators` tagnak engedi (403).
- **`portalWorklogs` alapértelmezésben `scope=authored`.** Ez minden olyan jegyet figyelembe vesz, amire a felhasználó logolt (`worklogAuthor` JQL), nem csak a „Munka felelőse”/assignee szerintieket. A régi viselkedés a `WORKLOG_SCOPE=assigned` beállítással kérhető vissza.
- **A `portalMyIssues` a megadott JQL-t használja `currentUser()`-rel**, a ScriptRunner `subtasksOf()` résszel együtt.

## 3. Futtatás a valódi Jirával

```bat
start-dev.cmd
```

Vagy kézzel: `cd backend` és `mvnw.cmd spring-boot:run`, egy másik ablakban pedig `cd frontend` és `npm run dev`.

A Jira címe alapértelmezésben `https://servicedesk.bnref.hu`. Ez a `JIRA_URL` környezeti változóval vagy az `application.yml`-ben módosítható.

### Belépés Jirával (céges SSO) – ajánlott

A portál a Jira OAuth 2.0 szolgáltatásával léptet be. A Jira a céges Microsoft/Entra ID SSO-t használja, így a felhasználó jelszót nem ír be a portálba. A portál a felhasználó saját tokenjével hívja a ScriptRunner végpontokat (READ jogosultság elég).

1. **Jira → Adminisztráció → Alkalmazások → Application links → Create link**
   - *External application*, **Incoming**
   - Redirect URL: `http://localhost:8080/login/oauth2/code/jira` (élesben: `https://<portál címe>/login/oauth2/code/jira`)
   - Permission: **Read**
2. Másold le a `backend/config/application.yml.example` fájlt **`backend/config/application.yml`** néven, és írd bele a Client ID-t és a Client secret-et. Ez a fájl nincs verziókezelve. Helyette a `JIRA_OAUTH_CLIENT_ID` és `JIRA_OAUTH_CLIENT_SECRET` környezeti változó is használható.
3. Indítás: `start-dev.cmd`. A belépőoldalon megjelenik a **„Belépés Jirával (céges SSO)”** gomb.

A belépés menete:

1. A portál a Jira engedélyező oldalára irányít.
2. Ha még nem vagy belépve, a Jira a Microsoft-os bejelentkezést kéri.
3. Az első alkalommal jóvá kell hagynod a hozzáférést (*Allow*).
4. A Jira visszairányít a portálra, és belép.

Az access tokent a backend lejárat előtt automatikusan frissíti a refresh tokennel. A biztonságot a PKCE (S256) és a `state` ellenőrzés adja. A jelszavas belépés tartalékként megmarad. Kikapcsolni a `PORTAL_PASSWORD_LOGIN=false` beállítással lehet.

Fejlesztéskor a Jira közvetlenül a backendre (8080) irányít vissza, a backend pedig a Vite-ra (`PORTAL_FRONTEND_URL=http://localhost:5173`). Ezt a `start-dev.cmd` beállítja.

### HTTPS-tanúsítvány (PKIX hiba)

A Jira tanúsítványát egy céges belső CA írta alá. Ezt a Windows ismeri, a Java saját tára (`cacerts`) viszont nem. Ezért a `start-dev.cmd` automatikusan a **Windows tanúsítványtárát** használja: `JIRA_TRUST_STORE_TYPE=Windows-ROOT`.

Kézi indításnál PowerShellben:

```powershell
$env:JIRA_TRUST_STORE_TYPE="Windows-ROOT"; .\mvnw.cmd spring-boot:run
```

Linux szerveren a CA-tanúsítványt egy truststore fájlba kell importálni (`keytool -importcert ...`). Ennek a beállításai: `JIRA_TRUST_STORE` (a fájl útvonala), `JIRA_TRUST_STORE_TYPE=PKCS12` és `JIRA_TRUST_STORE_PASSWORD`.

### Hogyan működik a belépés

| `JIRA_AUTH_MODE` | Mi történik |
|---|---|
| `session` (alapértelmezett) | A backend a `/rest/auth/1/session` végponttal belép a Jirába, és csak a Jira munkamenet-cookie-ját tartja meg a szerveroldali sessionben. A jelszót sehova nem menti. |
| `basic` | A felhasználónév és a jelszó a szerver memóriájában marad a session idejére, minden Jira-hívás Basic auth. Ez tartalék arra az esetre, ha a session mód nem működik. |

A böngésző csak egy HttpOnly, SameSite=Strict session cookie-t kap. A módosító kéréseket CSRF-token védi (`XSRF-TOKEN` cookie + `X-XSRF-TOKEN` fejléc). A belépési kísérletek száma felhasználónként 5, összesen 60 percenként. A session 8 óra után lejár. Ha a Jira-munkamenet közben lejár, a portál automatikusan visszadob a belépéshez.

## 4. Tesztek

```bat
cd backend
mvnw.cmd test
```

A tesztek ezt fedik le:

- **`WorklogCalculatorTest`:** munkanapi átlag (14 nap / 10 munkanap / 80 óra → 8,00), hétvégi órák, csak hétvégét tartalmazó időszak, ünnepnapok és áthelyezett munkanapok, folyamatban lévő és lezárt hónap, szökőév, 12 havi áttekintés.
- **`CookieHeadersTest`:** a Jira Set-Cookie fejléceinek feldolgozása.
- **`PortalApplicationTests`:** a teljes Spring kontextus mock Jirával, időszakos és havi riport, a jogosultság-ellenőrzés (nem admin nem kérhet le mást, admin igen).

A frontend típusellenőrzése: `cd frontend` és `npm run typecheck`.

## 5. Egy darab futtatható jar (élesítéshez)

```bat
cd frontend
npm run build
cd ..\backend
mvnw.cmd package
java -jar target\jira-portal-0.1.0-SNAPSHOT.jar
```

A `npm run build` a frontendet a `backend/src/main/resources/static` mappába építi, így a jar mindent kiszolgál a http://localhost:8080 címen. Élesben HTTPS reverse proxy mögé kell tenni, és be kell állítani a `COOKIE_SECURE=true` változót.

## Dashboard nézetek

A saját jegyek három nézetben láthatók:

- **Csoportosítva:** színes, összecsukható csoportok.
- **Tábla:** kanban-oszlopok kártyákkal.
- **Lista:** egyetlen rendezhető táblázat.

Csoportosítani státusz, állapot (teendő / folyamatban / kész), szerep, határidő (lejárt, ma/holnap, 7 napon belül…), prioritás vagy projekt szerint lehet. Minden csoport színe a paletta ikonnal átállítható. A választott nézetet, a csoportosítást, a színeket és az összecsukott csoportokat a böngésző felhasználónként megjegyzi (localStorage).

## Szabadságok

A Worklog monitor **Szabadságaim** paneljén mindenki megadhatja a saját távolléteit. Az időszakot kezdő és záró nappal kell megadni, a hétvégék automatikusan kimaradnak. A típus szabadság, betegszabadság vagy egyéb távollét lehet.

- A szabadságnap **nem munkanap**: kiesik a havi keretből és a munkanapi átlag osztójából. Az esetleg mégis logolt órák az összesben benne maradnak.
- A naptárban lilával jelennek meg, a napi grafikonon lila jel mutatja őket.
- Felhasználónként egy fájlban tárolódnak: `data/leaves/`.
- Az admin a más felhasználókra szűrt nézetben a kiválasztott személy szabadságaival számol, de azokat nem szerkesztheti.

## Telepítés Renderre (ingyenes, megosztható link)

Felépítés: a **Render** (ingyenes web service, Docker) futtatja a portált, az adatokat egy ingyenes **Neon** PostgreSQL adatbázis tárolja. A Render a GitHub-repóból épít, és minden push után automatikusan újratelepít.

### 1. GitHub (privát repó)

```powershell
cd "$env:USERPROFILE\dev\jira-portal"
git init -b main
git add .
git status          # a backend/config/application.yml (titok) NEM szerepelhet a listában!
git commit -m "Jira Portal"
```

A github.com-on hozz létre egy **privát** repót `jira-portal` néven (README nélkül), majd:

```powershell
git remote add origin https://github.com/<felhasznalonev>/jira-portal.git
git push -u origin main
```

### 2. Neon adatbázis

1. Regisztrálj a https://neon.tech oldalon (GitHub-fiókkal is lehet), és hozz létre egy új projektet: *jira-portal*, régió: **AWS Europe Central (Frankfurt)**.
2. A *Connection string* értéket másold ki (`postgresql://...neon.tech/...?sslmode=require`). Ez lesz a `PORTAL_DB_URL`.

A táblát a portál első induláskor maga hozza létre.

### 3. Render szolgáltatás

1. A https://render.com oldalon válaszd ezt: **New → Blueprint**. Válaszd ki a repót, a Render beolvassa a `render.yaml`-t.
2. A bekért értékek:
   - `PORTAL_DB_URL`: a Neon connection string.
   - `JIRA_OAUTH_CLIENT_ID`, `JIRA_OAUTH_CLIENT_SECRET`, `JIRA_OAUTH_REDIRECT_URI`: ha még nincs meg, egyelőre írj be `x`-et.
3. *Deploy*. Az első build néhány perc. Utána a Render megmutatja a címet, például `https://jira-portal.onrender.com`.

### 4. Jira Application Link az éles címhez

1. Jira → Application links → **új** bejövő OAuth 2.0 link (a fejlesztői linket ne töröld):
   - Név: `Jira Portal (Render)`
   - Redirect URL: `https://<render-cím>/login/oauth2/code/jira`
   - Permission: Read
2. A Renderen a szolgáltatás **Environment** fülén írd be a valódi `JIRA_OAUTH_CLIENT_ID` és `JIRA_OAUTH_CLIENT_SECRET` értéket, a `JIRA_OAUTH_REDIRECT_URI` pedig pontosan a fenti Redirect URL legyen. Mentés után a Render automatikusan újraindít.

### 5. Megosztás

A Render címét bárkinek elküldheted, akinek van Jira-fiókja. Ő a **Belépés Jirával** gombbal, a céges SSO-val lép be. Az első alkalommal jóvá kell hagynia a hozzáférést, és mindenki csak a saját Jira-jogosultsága szerinti adatokat látja. Nyilvános címen a jelszavas belépés ki van kapcsolva (`PORTAL_PASSWORD_LOGIN=false`).

**Az ingyenes csomag korlátai:**

- A szolgáltatás 15 perc tétlenség után elalszik. Az első megnyitás ilyenkor kb. 1 perc.
- Elalváskor a bejelentkezések megszűnnek, de az SSO-val ez egy kattintás.
- A szabadságok és a mentett személyek a Neonban vannak, ezért megmaradnak.

A fejlesztés helyben ugyanúgy folytatható (`start-dev.cmd`). Amikor egy állapot kész, a `git push` után a Render magától frissít.

## Beállítások (`backend/src/main/resources/application.yml`)

| Kulcs / környezeti változó | Jelentés |
|---|---|
| `JIRA_URL` | A Jira címe (csak HTTPS) |
| `JIRA_AUTH_MODE` | `session` / `basic` (csak a jelszavas belépésre vonatkozik) |
| `JIRA_OAUTH_CLIENT_ID`, `JIRA_OAUTH_CLIENT_SECRET` | Belépés Jirával: az Application Link adatai (vagy `backend/config/application.yml`) |
| `JIRA_OAUTH_REDIRECT_URI` | Az Application Link Redirect URL-je |
| `PORTAL_FRONTEND_URL` | Belépés utáni visszairányítás (fejlesztéskor `http://localhost:5173`, élesben üres) |
| `PORTAL_PASSWORD_LOGIN` | `false` = csak Belépés Jirával |
| `JIRA_TRUST_STORE_TYPE` | `Windows-ROOT` = a Windows tanúsítványtára (céges CA) |
| `WORKLOG_SCOPE` | `authored` / `assigned` |
| `COOKIE_SECURE` | `true` élesben (HTTPS) |
| `PORTAL_DATA_DIR` | Helyi adattárolás mappája, ha nincs adatbázis (alapértelmezés: `./data`) |
| `PORTAL_DB_URL` (+ opcionálisan `PORTAL_DB_USER`, `PORTAL_DB_PASSWORD`) | PostgreSQL / Neon a szabadságokhoz és a mentett személyekhez |
| `SERVER_FORWARD_HEADERS_STRATEGY=framework` | Reverse proxy (pl. Render) mögött kell, hogy a https-átirányítások helyesek legyenek |
| `portal.worklog.holidays` | Hétköznapra eső ünnepnapok, `YYYY-MM-DD` formátumban (nem számítanak a keretbe és az átlagba) |
| `portal.worklog.extra-workdays` | Áthelyezett szombati munkanapok |
| `portal.worklog.project-presets` | A Worklog oldal projektszűrő gombjai |

## API röviden

| Végpont | Leírás |
|---|---|
| `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, `GET /api/auth/options` | Belépés és munkamenet |
| `GET /api/auth/oauth/start` → Jira → `GET /login/oauth2/code/jira` | Belépés Jirával (OAuth 2.0 + PKCE) |
| `GET /api/dashboard/issues` | Saját jegyek |
| `GET /api/worklog/report?view=period&days=14&projects=AJI,BNSD[&user=]` | Időszakos riport |
| `GET /api/worklog/report?view=month&month=2026-11` | Havi riport |
| `GET /api/worklog/overview` | Az elmúlt 12 hónap összesítője |
| `GET /api/worklog/export?...` | CSV export (a riporttal azonos paraméterek) |
| `GET/PUT /api/worklog/people` | Az admin mentett személylistája |
| `GET /api/worklog/leaves[?user=]`, `PUT /api/worklog/leaves` | Szabadságnapok (a mentés mindig a saját listára vonatkozik) |

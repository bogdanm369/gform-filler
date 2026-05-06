# gform-filler

Continuous Google Form filler for one or more configured forms, now packaged as a Spring Boot console application.
( I needed it for school homework )

## Project layout

- `ro.bogdanm.tools` - Spring Boot entry point
- `ro.bogdanm.tools.config` - external config loading and runtime bean wiring
- `ro.bogdanm.tools.client` - Google Forms HTTP and HTML parsing
- `ro.bogdanm.tools.model` - immutable data records and enums
- `ro.bogdanm.tools.service` - orchestration and question-file handling
- `ro.bogdanm.tools.worker` - long-running worker loop per form job

## What it does

- starts one worker thread per configured form link
- fetches the Google Form metadata on every tick
- if the configured question file is missing, exports a template locally instead of submitting
- after you fill in `question.N.values`, submits the form repeatedly at the configured interval
- uses fresh stateless HTTP requests on every run, so it behaves like an incognito-style session (no persisted cookies or browser profile)

## Supported question types

Currently supported:

- short text
- paragraph text
- multiple choice
- dropdown
- checkboxes
- linear scale
- date
- time

Section headers and images are exported as metadata only and are not answerable.
Unsupported Google Forms question types are exported with type `UNSUPPORTED` so you can spot them quickly.

## Configuration file

Default config path:

```powershell
config\formfiller.properties
```

Start with the included sample and adjust it.

Main properties:

```properties
formfiller.names=one,two
formfiller.one.link=https://docs.google.com/forms/...
formfiller.one.questions-file=questionnaires/one.questions.properties
formfiller.one.interval-seconds=60
formfiller.one.fill-count=25
formfiller.one.enabled=true

formfiller.two.link=https://docs.google.com/forms/...
formfiller.two.questions-file=questionnaires/two.questions.properties
formfiller.two.interval-seconds=100-300
formfiller.two.fill-count=0
```

`formfiller.<name>.interval-seconds` supports either:

- a fixed value like `60`
- an inclusive range like `100-300`

When you use a range, the worker picks a fresh random interval inside that range for every tick.

`formfiller.<name>.fill-count` is a fixed integer telling that worker how many fills/submission attempts to perform for that form:

- `0` → run forever
- `1` → fill once, then stop
- `25` → fill 25 times, then stop

For backward compatibility, the older property `formfiller.<name>.max-runs` is still accepted as an alias, but `fill-count` is the preferred name.

## Question file format

When a question file is missing, the app writes a template like this:

```properties
question.1.type=CHECKBOX
question.1.required=true
question.1.title=Am peste 18 ani...
question.1.options=1: DA
question.1.values=
```

You only need to edit `question.N.values`.

### Value syntax

Set only `question.N.values`.

### Choice questions

```properties
question.2.values=5
question.3.values=1-3
question.4.values=1,3,5
question.5.values=5(90%),4(10%)
question.6.values=4-5,3(10%),2(5%)
question.7.values=multi:1,3-4
```

- `5` → always choose option 5
- `1-3` → choose one option randomly from 1, 2, 3
- `1,3,5` → choose one option randomly from 1, 3, 5
- `5(90%),4(10%)` → weighted single-choice distribution
- `4-5,3(10%),2(5%)` → about 85% choose from 4 or 5, 10% choose 3, 5% choose 2
- `multi:1,3-4` → for checkboxes, tick all listed options

Notes:
- weighted percentages are probabilistic per run, not exact global caps
- a leading `=` is optional for weighted syntax, for example `=4-5,3(10%),2(5%)`
- if a pure weighted list totals less than 100%, the values are treated as relative weights

### Text / date / time questions

Use a single value to submit it as-is:

```properties
question.6.values=Accounting
question.7.values=2026-05-06
question.8.values=09:30
```

Use a comma-separated text list to pick one value randomly:

```properties
question.9.values=HR,IT,Contabilitate
question.10.values="HR, Payroll",IT,Contabilitate
question.11.values=Didactic(60%),HR(5%),IT(5%),Contabilitate(10%),Administratie(10%)
```

- `HR,IT,Contabilitate` → choose one text value randomly
- `"HR, Payroll",IT,Contabilitate` → quoted text keeps the comma inside one candidate
- `Didactic(60%),HR(5%),IT(5%)` → weighted text distribution

Use numeric list/range syntax to pick one numeric value randomly:

```properties
question.12.values=18,19,20,21
question.13.values=18-30
question.14.values=18-30,45(10%)
```

- `18,19,20,21` → choose one numeric value randomly
- `18-30` → choose one numeric value randomly from the range
- `18-30,45(10%)` → about 90% choose from 18-30, 10% choose 45

## Run

### IntelliJ IDEA

Run the Spring Boot main class `ro.bogdanm.tools.Main`.

Recommended run configuration:

- type: **Spring Boot**
- main class: `ro.bogdanm.tools.Main`
- working directory: the project root (`gform-filler`)
- JDK: 21+ (the project is configured for Java 21 and also worked in validation on Java 25)

Optional overrides:

- Program arguments: `--formfiller.config-file=C:\path\to\formfiller.properties`
- Or environment variable: `FORMFILLER_CONFIG=C:\path\to\formfiller.properties`

If the default relative path `config\formfiller.properties` is used, the app now also tries to resolve it relative to the `gform-filler` project or jar location, so accidental IntelliJ working-directory mismatches are less likely to break startup.

If IntelliJ still shows old dependency errors after opening the project, reload the Maven project once so it picks up the new Spring Boot dependencies.

### Maven

From the project root:

```powershell
Set-Location "C:\Work\ide-projects\java\gform-filler"
mvn test
mvn spring-boot:run
```

Or point to a different config file:

```powershell
Set-Location "C:\Work\ide-projects\java\gform-filler"
mvn spring-boot:run -Dspring-boot.run.arguments="--formfiller.config-file=C:\Work\ide-projects\java\gform-filler\config\formfiller.properties"
```

### Packaged jar

```powershell
Set-Location "C:\Work\ide-projects\java\gform-filler"
mvn clean package
java -jar .\target\gform-filler-1.0.0-SNAPSHOT.jar --formfiller.config-file="C:\Work\ide-projects\java\gform-filler\config\formfiller.properties"
```

## Typical first run

1. Start the app.
2. The app downloads the form HTML and writes the missing question file.
3. Edit the generated `questionnaires\*.questions.properties` file and set `question.N.values`.
4. Leave the app running; on the next interval tick it starts submitting.

## Notes

- Google may change internal form metadata over time; if that happens, the parser may need adjustment.
- Some forms can contain anti-abuse checks or unsupported question types.
- Use responsibly and only on forms you are allowed to automate.


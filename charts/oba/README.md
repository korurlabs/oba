# Oba Helm chart

Runs Oba on Kubernetes: the Spring Boot API (`api`) and the six Next.js web apps `kazan`, `kaya`, `kalkan`, `otak`, `ulak` and `account`. Each component is one Deployment, one Service, one ServiceAccount and, when you enable them, one Ingress and one PodDisruptionBudget.

The chart creates no Secret, database or identity provider. You bring PostgreSQL, an OpenID Connect issuer (Oba is developed against Keycloak) and the environment of each component.

## Install

```sh
helm install oba oci://ghcr.io/korurlabs/charts/oba --version <version> -f my-values.yaml
```

The chart version, its `appVersion` and the tag of every image are the same release number. The images are `ghcr.io/korurlabs/oba-<component>:<version>`.

## Object names

Every object is named `<fullname>-<component>`, and each Service `<fullname>-<component>-service`. `<fullname>` is the release name when it contains `oba`, otherwise `<release>-oba`; set `fullnameOverride` to choose it. The pods of a component are selected by the single label `app: <fullname>-<component>`.

## Values

Every component has the same keys, under `api`, `kazan`, `kaya`, `kalkan`, `otak`, `ulak` and `account`:

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Render the component. |
| `replicas` | `1` | Pod count. |
| `image.repository` | `ghcr.io/korurlabs/oba-<component>` | Image. |
| `image.tag` | the chart's `appVersion` | Image tag. |
| `command`, `args` | the image's command | Container command. |
| `env` | see `values.yaml` | Plain environment entries, in the Kubernetes `env` form. Setting it replaces the default list. |
| `envFrom` | `[]` | Secrets or ConfigMaps to load as environment, for example `[{secretRef: {name: oba-api}}]`. |
| `resources` | see `values.yaml` | Requests and limits. |
| `startupProbe`, `livenessProbe`, `readinessProbe` | see `values.yaml` | Probes; `{}` turns one off. |
| `strategy` | rolling, `maxUnavailable: 0`, `maxSurge: 1` | Deployment strategy. |
| `podSecurityContext`, `securityContext` | user and group 10001, `runAsNonRoot`, read-only root filesystem, no privilege escalation, all capabilities dropped, seccomp `RuntimeDefault` | Pod and container security context. |
| `volumes`, `volumeMounts` | an emptyDir on `/tmp`; web apps also one on `/app/.next/standalone/apps/<app>/.next/cache` | Writable paths on the read-only root filesystem. Setting either replaces the default list, so keep these entries. |
| `podLabels`, `podAnnotations` | `{}` | Added to the pod template, for example for a secret-injection webhook. |
| `nodeSelector`, `tolerations`, `affinity` | empty | Scheduling. |
| `serviceAccount.create`, `serviceAccount.annotations` | `true`, `{}` | ServiceAccount. Its token is never mounted. |
| `service.type`, `service.port`, `service.annotations` | `ClusterIP`, `80`, `{}` | Service. The container listens on 8080. |
| `ingress.enabled`, `className`, `annotations`, `hosts`, `tls` | off | A standard Ingress. `hosts` is a list of `{host, paths: [{path, pathType}]}`; `paths` defaults to `/` with `Prefix`. |
| `pdb.enabled`, `pdb.maxUnavailable` | `false`, `1` | PodDisruptionBudget. |

Top level: `nameOverride`, `fullnameOverride`, `imagePullSecrets` and `commonLabels`.

## Environment

### api

| Key | Required | Meaning |
|---|---|---|
| `SPRING_DATASOURCE_URL` | yes | JDBC URL of the PostgreSQL database, for example `jdbc:postgresql://postgres:5432/oba`. Flyway migrates it when the API starts. |
| `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | yes | Database credentials. |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | yes | Issuer URL of the OpenID Connect realm whose tokens the API accepts. |
| `ENCRYPTION_SECRET` | yes | Key for the values the API encrypts at rest, for example `openssl rand -hex 32`. Keep it: a new key cannot read what the old one encrypted. |
| `AZURE_STORAGE_CONNECTION_STRING`, `AZURE_STORAGE_BLOB_BASE_URL`, `AZURE_STORAGE_CONTAINER_DEFAULT` | yes | Azure Blob Storage (or Azurite) for uploaded files. |
| `CONFIG_SIGNING_KEY`, `CONFIG_SIGNING_KEY_ID`, `CONFIG_SIGNING_KEY_VALID_FROM` | yes | The key that signs the agent configuration Kaya delivers. |
| `CONFIG_SIGNING_PREVIOUS_KEY_ID`, `CONFIG_SIGNING_PREVIOUS_PUBLIC_KEY`, `CONFIG_SIGNING_PREVIOUS_VALID_TO` | no | The previous signing key, during a key rotation. |
| `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`, `MISTRAL_API_KEY`, `DEEPSEEK_API_KEY`, `GROK_API_KEY` | no | AI providers. |
| `MS_GRAPH_CLIENT_ID`, `MS_GRAPH_CLIENT_SECRET`, `MS_GRAPH_TENANT_ID` | no | Microsoft Graph, for Outlook mail. |
| `KAZAN_GITHUB_APP_ID`, `KAZAN_GITHUB_INSTALLATION_ID`, `KAZAN_GITHUB_ORGANIZATION_ID`, `KAZAN_GITHUB_PRIVATE_KEY` | no | Credentials of the GitHub App that links repositories and pull requests. |
| `KAZAN_ALERTMANAGER_ORGANIZATION_ID`, `KAZAN_ALERTMANAGER_WEBHOOK_TOKEN` | no | The Alertmanager webhook that files incident tickets. |
| `KAZAN_EXPO_ACCESS_TOKEN` | no | Push notifications to the mobile app. |
| `KAYA_SESSION_RETENTION_CRON` | no | Schedule of the Kaya session clean-up, default `0 30 3 * * *`. |

The chart sets `SPRING_PROFILES_ACTIVE=prod` and `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=60.0`.

### Web apps

Every web app reads the same keys:

| Key | Meaning |
|---|---|
| `BASE_URL` | Public URL of this app. |
| `API_BASE_URL` | Public URL of the API. |
| `AUTH_URL` | Public origin of this app for Auth.js, usually the same as `BASE_URL`. |
| `AUTH_SECRET` | Auth.js session secret, for example `openssl rand -base64 32`. |
| `AUTH_KEYCLOAK_ISSUER` | Issuer URL of the OpenID Connect realm. |
| `AUTH_KEYCLOAK_ID`, `AUTH_KEYCLOAK_SECRET` | The OpenID Connect client of this app. |
| `KAZAN_URL`, `KAYA_URL`, `KALKAN_URL`, `OTAK_URL`, `ULAK_URL`, `ACCOUNT_URL` | Public URLs of the other apps, for the app switcher. |

The chart sets `HOSTNAME=0.0.0.0`, so that Next.js listens on every interface of the pod.

## Example

```yaml
api:
  envFrom:
    - secretRef:
        name: oba-api
  ingress:
    enabled: true
    className: nginx
    hosts:
      - host: api.example.com
    tls:
      - secretName: api-example-com-tls
        hosts: [api.example.com]
kazan:
  envFrom:
    - secretRef:
        name: oba-kazan
  ingress:
    enabled: true
    className: nginx
    hosts:
      - host: kazan.example.com
# Turn off the apps you do not use.
otak:
  enabled: false
```

## Upgrades

The API migrates the database when it starts, and a rolling update starts the new pod before the old one stops, so for a short time the previous release runs on the new schema. Every Oba release keeps its migrations compatible with the release before it. Flyway never rolls back: to go back a release, the schema must still suit it.

## License

AGPL-3.0-only. Source: https://github.com/korurlabs/oba

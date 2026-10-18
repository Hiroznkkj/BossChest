package br.hirozn.bauchefe.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Atualização automática pelo GitHub Releases. Só no PC.
 * Baixa a versão nova para a pasta mods e, quando o jogo fecha, apaga o jar antigo
 * (o Windows não deixa apagar um jar em uso, então um processo separado espera o jogo sair).
 */
public final class Atualizador {
	/** "dono/repositorio" no GitHub. */
	public static final String REPO = "Hiroznkkj/BossChest";

	/** Mensagem para mostrar no chat ao entrar no mundo (null = nada a avisar). */
	public static volatile String aviso;

	private Atualizador() {}

	public static void iniciar() {
		if (REPO.startsWith("COLOCAR")) return;
		Thread t = new Thread(Atualizador::verificar, "bauchefe-atualizador");
		t.setDaemon(true);
		t.start();
	}

	private static void verificar() {
		try {
			Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer("bauchefe");
			if (mod.isEmpty()) return;
			Path atual = mod.get().getOrigin().getPaths().stream().findFirst().orElse(null);
			if (atual == null || !Files.isRegularFile(atual) || !atual.toString().endsWith(".jar")) return; // ambiente de dev
			Version instalada = mod.get().getMetadata().getVersion();

			HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
					.followRedirects(HttpClient.Redirect.NORMAL).build();
			HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + REPO + "/releases/latest"))
					.header("Accept", "application/vnd.github+json").header("User-Agent", "bauchefe-updater")
					.timeout(Duration.ofSeconds(15)).build();
			HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
			if (res.statusCode() != 200) return;
			JsonObject rel = JsonParser.parseString(res.body()).getAsJsonObject();
			String tag = rel.get("tag_name").getAsString().replaceFirst("^[vV]", "");
			Version nova = SemanticVersion.parse(tag);
			if (nova.compareTo(instalada) <= 0) return;

			String url = null, nome = null;
			for (JsonElement a : rel.getAsJsonArray("assets")) {
				String n = a.getAsJsonObject().get("name").getAsString();
				if (n.toLowerCase(Locale.ROOT).endsWith(".jar") && !n.contains("sources")) {
					url = a.getAsJsonObject().get("browser_download_url").getAsString();
					nome = n;
					break;
				}
			}
			if (url == null) return;

			Path mods = atual.getParent();
			Path destino = mods.resolve(nome);
			if (destino.equals(atual)) return;
			Path tmp = mods.resolve(nome + ".baixando");
			HttpResponse<InputStream> dl = http.send(HttpRequest.newBuilder(URI.create(url))
					.header("User-Agent", "bauchefe-updater").timeout(Duration.ofMinutes(2)).build(), HttpResponse.BodyHandlers.ofInputStream());
			if (dl.statusCode() != 200) return;
			try (InputStream in = dl.body()) { Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING); }
			if (Files.size(tmp) < 1024) { Files.deleteIfExists(tmp); return; }
			Files.move(tmp, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

			apagarAoSair(atual);
			aviso = "§6[Baú Chefe] §fVersão " + tag + " baixada. §eFeche e abra o jogo§f para usar.";
		} catch (Exception e) {
			System.err.println("[bauchefe] não consegui verificar atualização: " + e);
		}
	}

	/** Agenda a remoção do jar antigo para depois que o jogo fechar. */
	private static void apagarAoSair(Path velho) {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				String p = velho.toAbsolutePath().toString();
				ProcessBuilder pb = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")
						? new ProcessBuilder("cmd", "/c", "ping -n 6 127.0.0.1 >nul & del /f /q \"" + p + "\"")
						: new ProcessBuilder("sh", "-c", "sleep 5; rm -f -- \"$0\"", p);
				pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			} catch (Exception ignored) {
			}
		}));
	}
}

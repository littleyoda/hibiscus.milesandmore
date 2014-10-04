package org.jameica.hibiscus.milesandmore;

import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Resource;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.SilentCssErrorHandler;
import com.gargoylesoftware.htmlunit.ThreadedRefreshHandler;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;

import de.willuhn.jameica.hbci.Settings;
import de.willuhn.jameica.hbci.messaging.SaldoMessage;
import de.willuhn.jameica.hbci.rmi.Konto;
import de.willuhn.jameica.hbci.rmi.Umsatz;
import de.willuhn.jameica.hbci.synchronize.jobs.SynchronizeJobKontoauszug;
import de.willuhn.jameica.system.Application;
import de.willuhn.logging.Logger;
import de.willuhn.util.ApplicationException;
import de.willuhn.util.I18N;

/**
 * Implementierung des Kontoauszugsabruf fuer AirPlus.
 * Von der passenden Job-Klasse ableiten, damit der Job gefunden wird.
 */
public class MMSynchronizeJobKontoauszug extends SynchronizeJobKontoauszug implements MMSynchronizeJob
{
	private final static I18N i18n = Application.getPluginLoader().getPlugin(Plugin.class).getResources().getI18N();

	@Resource
	private MMSynchronizeBackend backend = null;

	private DateFormat df = new SimpleDateFormat("dd/MM/yyyy", Locale.GERMAN);
	/**
	 * @see org.jameica.hibiscus.barclaystg.AirPlusSynchronizeJob#execute()
	 */
	@Override
	public void execute() throws Exception
	{
		Konto konto = (Konto) this.getContext(CTX_ENTITY); 

		Logger.info("Rufe Umsätze ab für " + backend.getName());

		////////////////
		String username = konto.getKundennummer();
		String password = konto.getMeta(MMSynchronizeBackend.PROP_PASSWORD, null);
		if (username == null || username.length() == 0)
			throw new ApplicationException(i18n.tr("Bitte geben Sie Ihren Karten-Nummer in den Synchronisationsoptionen ein"));

		if (password == null || password.length() == 0)
			password = Application.getCallback().askPassword("Miles & More");

		Logger.info("username: " + username);
		////////////////


		List<Umsatz> fetched = doOneAccount(konto, username, password);

		Utils.abgleichAlteundNeueUmsätze(konto, fetched);

		Application.getMessagingFactory().sendMessage(new SaldoMessage(konto));
	}

	public List<Umsatz> doOneAccount(Konto konto, String username, String password) throws Exception {
		ArrayList<String> seiten = new ArrayList<String>();
		try {
			List<Umsatz> umsaetze = new ArrayList<Umsatz>();

			final WebClient webClient = new WebClient();
			webClient.getBrowserVersion().setBrowserLanguage("de-de");
			webClient.getBrowserVersion().setSystemLanguage("de-de");
			webClient.getBrowserVersion().setUserLanguage("de-de");
			webClient.getOptions().setRedirectEnabled(true);
			webClient.getOptions().setThrowExceptionOnScriptError(false);
			webClient.setCssErrorHandler(new SilentCssErrorHandler());
			webClient.setRefreshHandler(new ThreadedRefreshHandler());

			// Login-Page und Login
			HtmlPage page = webClient.getPage("https://www.miles-and-more.com/online/portal/mam/de/profilelogin?l=de&cid=18002");
			seiten.add(page.asXml()); // 0
			
			HtmlForm form =  new XPathIterator<HtmlForm>(page, "//form", "Login-Formular") {
				public boolean matches(HtmlForm f) {
					return f.getAttribute("id") != null && f.getAttribute("id").endsWith("mam-usm-userid-form");
				}
			}.getOne();

			seiten.add(form.asXml()); // 1
			HtmlInput userid =  new XPathIterator<HtmlInput>(form, "//input", "Input Userid") {
				@Override
				public boolean matches(HtmlInput f) {
					return f.getAttribute("id") != null && f.getAttribute("id").endsWith("-fld-userid");
				}
			}.getOne();

			HtmlInput inpPassword =  new XPathIterator<HtmlInput>(form, "//input", "Input Password") {
				@Override
				public boolean matches(HtmlInput f) {
					if (f.getAttribute("id") != null && f.getAttribute("id").endsWith("-fld-password")) {
						System.out.println(f.asText() + " " + f.getAttributesMap().entrySet());
					}
					return f.getAttribute("id") != null && f.getAttribute("id").endsWith("-fld-password");
				}
			}.getOne();

			HtmlButton button =  new XPathIterator<HtmlButton>(form, ".//button", "Login Button") {
				@Override
				public boolean matches(HtmlButton f) {
					return f.getAttribute("type") != null && f.getAttribute("type").equals("submit");
							
				}
			}.getOne();

			userid.setValueAttribute(username);
			inpPassword.setValueAttribute(password);
			page = button.click();
			seiten.add(page.asXml()); // 2

			// Auf die Seite mit den Punkten gehen
			page = webClient.getPage("https://www.miles-and-more.com/online/myportal/mam/de/account/account_statement?l=de&cid=18002");
			seiten.add(page.asXml()); // 3

			HtmlTable tab =  new XPathIterator<HtmlTable>(page, "//table", "Punktetabelle") {
				@Override
				public boolean matches(HtmlTable f) {
					return f.asText().startsWith("Datum");
							
				}
			}.getOne();
			
			seiten.add(tab.asXml()); // 4

			String[] current = null;
			for (int i = 1; i < tab.getRows().size() - 1; i++) {
				HtmlTableRow zeile = tab.getRows().get(i);
				if (zeile.getCells().size() != 4) {
					continue;
				}
				if (!zeile.getCells().get(0).asText().trim().equals("")) {
					store(current, umsaetze, konto);
					current = new String[4];
					for (int j = 0; j < 4; j++) {
						current[j] = "";
					}
				}
				for (int j = 0; j < 4; j++) {
					String s = Utils.remove1310(zeile.getCells().get(j).asText());
					if (s.isEmpty()) {
						continue;
					}
					if (!current[j].isEmpty()) {
						current[j] += "\n";
					}
					current[j] += s; 
				}

			}
			
			
			HtmlTableRow summenzeile = tab.getFooter().getRows().get(0);
			seiten.add(summenzeile.asXml()); // 5
			konto.setSaldo(string2float(summenzeile.getCell(2).asText().trim()));

			store(current, umsaetze, konto);


			HtmlAnchor logout =  new XPathIterator<HtmlAnchor>(page, "//a", "Logout") {
				@Override
				public boolean matches(HtmlAnchor f) {
					return f.asText().contains("Logout") || f.asText().contains("Abmeld") 
							|| f.asXml().contains("Logout") || f.asXml().contains("Abmeld");
							
				}
			}.getOne();
			page = logout.click();
			seiten.add(page.asXml()); // 6
			webClient.closeAllWindows();
			konto.store();
			return umsaetze;
		} catch (Exception ae) {
			throw ae;
		} finally {
			Utils.debug(Utils.getWorkingDir(Plugin.class), backend.getName(), 
					konto.getMeta(MMSynchronizeBackend.PROP_OPTIONS, ""), seiten);
		}
	}




	private void store(String[] current, List<Umsatz> umsaetze, Konto konto) throws RemoteException, ParseException {
		if (current == null) {
			return;
		}
		if (current[2].equals("")) {
			current[2] = "0"; // Sonderfall. Es gibt teilweise Einträge ohne Punkte
		}
		Umsatz newUmsatz = (Umsatz) Settings.getDBService().createObject(Umsatz.class,null);
		newUmsatz.setKonto(konto);
		newUmsatz.setBetrag(string2float(current[2]));
		newUmsatz.setDatum(df.parse(current[0]));
		newUmsatz.setValuta(df.parse(current[0]));
		String zweck = current[1];
		if (!current[3].isEmpty()) {
			zweck += "\nStatusmeilen: " + current[3].replace(" ", "").trim();
		}
		newUmsatz.setWeitereVerwendungszwecke(Utils.parse(zweck));
		umsaetze.add(newUmsatz);
	}

	/**
	 * - Tausender Punkte entfernen
	 * - Komma durch Punkt ersetzen
	 * @param s
	 * @return
	 */
	public static float string2float(String s) {
		try {
			return Float.parseFloat(s.replace(".", "").replace(",", "."));
		} catch (Exception e) {
			throw new RuntimeException("Cannot convert " + s + " to float");
		}

	}


}





import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import os.simulation.Application;
import os.simulation.AutoCreate;
import os.simulation.Container;
import os.simulation.Item;
import os.simulation.Operation;
import os.simulation.Thread;

/*
 * Na jednom seoskom vašaru, najinteresantnija atrakcija desetogodišnjacima je
 * velika trambolina starog Pere. Stari Pera je dobrog srca i pušta mališane da
 * se besplatno zabavljaju na njoj, na opštu radost njihovih roditelja. Mališa-
 * ni takođe koriste ovo i non-stop skaču na trambolini, silazeći jedino kada
 * se umore od skakanja kako bi se malo odmorili za novu rundu zabave.
 * 
 * A)
 * 
 * Nažalost, i trambolina starog Pere je dosta stara pa ne može izdržati više
 * od 300 kila. Prilikom implementacije rešenja imati ovo u vidu i ne dozvoliti
 * da se trambolina pokida. Potrebno je blokirati mališane koji žele da skaču
 * na trambolini ako bi ukupna težina prešla 300 kila.
 * 
 * B)
 * 
 * Zbog povećane mogućnosti povreda kada više dece skače na trambolini, stari
 * Pera ne dozvoljava da na njoj bude više od 5 dece. Takođe, ne terati mališa-
 * ne da nepotrebno čekaju ako ima mesta na trambolini.
 * 
 * C)
 * 
 * Kako su dečaci nestašniji i manje paze da nekog slučajno ne udare tokom ska-
 * kanja, potrebno je odvojiti dečake i devojčice, tj. blokirati ulaz devojči-
 * cama ako na trambolini trenutno skaču dečaci, odnosno dečacima ako je trenu-
 * tno koriste devojčice.
 */
public class DecaITrambolinaBrave extends Application {

	private class StariPeraA {

		private Lock brava = new ReentrantLock();
		private Condition uslov = brava.newCondition();

		private int preostalaTezina;

		public StariPeraA(int maxTezina) {
			this.preostalaTezina = maxTezina;
		}

		public void pustiUnutra(int tezina) throws InterruptedException {
			brava.lock();
			try {
				while (preostalaTezina - tezina < 0) {
					uslov.await();
				}
				preostalaTezina -= tezina;
			} finally {
				brava.unlock();
			}
		}
		
		public void pustiNapolje(int tezina) {
			brava.lock();
			try {
				preostalaTezina += tezina;
				uslov.signalAll(); // Izlaskom jednog tezeg deteta moze da udje vise lakse dece
				                   // Takodje, ako ceka lakse (koje moze da udje) i teze dete (koje ne moze da udje), sa signal() nemamo garancije da cemo pogoditi ono lakse
			} finally {
				brava.unlock();
			}
		}
	}

	private class StariPeraB {

		private Lock brava = new ReentrantLock();
		private Condition uslov = brava.newCondition();

		private int brSlobodno;

		public StariPeraB(int maxBr) {
			this.brSlobodno = maxBr;
		}

		public void pustiUnutra() throws InterruptedException {
			brava.lock();
			try {
				while (brSlobodno == 0) {
					uslov.await();
				}
				brSlobodno--;
			} finally {
				brava.unlock();
			}
		}
		
		public void pustiNapolje() {
			brava.lock();
			try {
				brSlobodno++;
				if (brSlobodno == 1) { // Obavestavamo samo je ranije nije bilo mesta i neko je mogao da ceka
					uslov.signal();    // Izlaskom jednog, samo jedan moze da udje
				}
			} finally {
				brava.unlock();
			}
		}
	}

	private class StariPeraC {

		private Lock brava = new ReentrantLock();
		private Condition decaci = brava.newCondition();
		private Condition devojcice = brava.newCondition();

		private int brDecaka = 0;
		private int brDevojcica = 0;

		public void pustiUnutra(Pol pol) throws InterruptedException {
			brava.lock();
			try {
				if (pol == Pol.MUSKI) {
					while (brDevojcica > 0) {
						decaci.await();
					}
					brDecaka++;
				} else {
					while (brDecaka > 0) {
						devojcice.await();
					}
					brDevojcica++;
				}
			} finally {
				brava.unlock();
			}
		}
		
		public void pustiNapolje(Pol pol) {
			brava.lock();
			try {
				if (pol == Pol.MUSKI) {
					brDecaka--;
					if (brDecaka == 0) { // Izlaskom poslednjeg decaka, sve devojcice mogu da udju
						devojcice.signalAll();
					}
				} else {
					brDevojcica--;
					if (brDevojcica == 0) { // Izlaskom poslednje devojcice, svi decaci mogu da udju
						decaci.signalAll();
					}
				}
			} finally {
				brava.unlock();
			}
		}
	}

	private class StariPeraABC {

		private Lock brava = new ReentrantLock();
		private Condition decaci = brava.newCondition();
		private Condition devojcice = brava.newCondition();

		private int brDecaka = 0;
		private int brDevojcica = 0;
		private int ukupnaTezina = 0;

		private final int maxTezina;
		private final int maxBr;

		public StariPeraABC(int maxTezina, int maxBr) {
			this.maxTezina = maxTezina;
			this.maxBr = maxBr;
		}

		public void pustiUnutra(Pol pol, int tezina) throws InterruptedException {
			brava.lock();
			try {
				if (pol == Pol.MUSKI) {
					while (brDevojcica > 0 || brDecaka >= maxBr || ukupnaTezina + tezina > maxTezina) {
						decaci.await();
					}
					brDecaka++;
				} else {
					while (brDecaka > 0 || brDevojcica >= maxBr || ukupnaTezina + tezina > maxTezina) {
						devojcice.await();
					}
					brDevojcica++;
				}
				ukupnaTezina += tezina;
			} finally {
				brava.unlock();
			}
		}
		
		public void pustiNapolje(Pol pol, int tezina) {
			brava.lock();
			try {
				ukupnaTezina -= tezina;
				if (pol == Pol.MUSKI) {
					brDecaka--;
					decaci.signalAll();  // Izlaskom jednog (tezeg) decaka, mozda moze da udje vise drugih (laksih) decaka
					if (brDecaka == 0) { // Izlaskom poslednjeg decaka, sve devojcice mogu da udju
						devojcice.signalAll();
					}
				} else {
					brDevojcica--;
					devojcice.signalAll();
					if (brDevojcica == 0) {
						decaci.signalAll();
					}
				}
			} finally {
				brava.unlock();
			}
		}
	}

	protected final int MAX_TEZINA = 300;
	protected final int MAX_BR_DECE = 5;

	protected StariPeraABC pera = new StariPeraABC(MAX_TEZINA, MAX_BR_DECE);

	protected enum Pol {
		MUSKI, ZENSKI;
	}

	@AutoCreate(26)
	protected class Dete extends Thread {

		private final Pol pol = randomElement(Pol.values());
		private final int tezina = randomInt(25, 60);

		public Dete() {
			setName(String.format("%4.1f kg", 1.0 * tezina));
			setColor(pol == Pol.MUSKI ? AZURE : ROSE);
		}

		@Override
		protected void step() {
			odmara();
			try {
				pera.pustiUnutra(pol, tezina);
				try {
					skace();
				} finally {
					pera.pustiNapolje(pol, tezina);
				}
			} catch (InterruptedException e) {
				stopGracefully();
			}
		}
	}

	// ------------------- //
	//    Sistemski deo    //
	// ------------------- //
	// Ne dirati kod ispod //
	// ------------------- //

	protected final Container van       = box("Клупе").color(OLIVE);
	protected final Container unutra    = box("Трамболина").color(ARMY);
	protected final Container main      = column(van, unutra);
	protected final Operation dete      = init().container(van).name("Дете %d").color(ORANGE);

	protected final Operation odmaranje = duration("7±2").text("Одмара").textAfter("Чека");
	protected final Operation skakanje  = duration("5±2").text("Скаче").container(unutra).update(this::azuriraj);

	protected void odmara() {
		odmaranje.performUninterruptibly();
	}

	protected void skace() {
		skakanje.performUninterruptibly();
	}

	protected void azuriraj(Item item) {
		int br = 0;
		double tezina = 0.0;
		for (Dete dete : unutra.getItems(Dete.class)) {
			br += 1;
			tezina += dete.tezina;
		}
		unutra.setText(String.format("%4.2f kg / %d", tezina, br));
		if (tezina > MAX_TEZINA || br > MAX_BR_DECE) {
			unutra.setColor(MAROON);
		} else {
			unutra.setColor(ARMY);
		}
	}

	@Override
	protected void initialize() {
		azuriraj(null);
	}

	public static void main(String[] arguments) {
		launch("Деца и трамболина");
	}
}
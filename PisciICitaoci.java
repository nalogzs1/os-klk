package os.examples.classical.gui.solutions;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Data je zajednicka baza podataka. Vise procesa zeli da istovremeno pristupa
 * ovoj bazi kako bi citali ili upisivali podatke u nju. Kako bi korektno
 * realizovali ove istovremene pristupe bez rizika da dodje do problema,
 * procesi moraju da postuju sledeca pravila: istovremena citanja su dozvoljena
 * posto citaoci ne smetaju jedan drugom, istovremeno citanje i pisanje nije
 * dozvoljeno jer se moze desiti da citalac procita pogresne podatke (do pola
 * upisane), istovremena pisanja takodje nisu dozvoljena jer mogu prouzrokovati
 * ostecenje podataka.
 * 
 * Implementirati sinhronizaciju procesa pisaca i procesa citalaca tako da se
 * postuju opisana pravila.
 */
public class PisciICitaoci extends Application {

	protected final BazaLock baza = new BazaLock();

	protected final class BazaSync {

		private int brPisaca;
		private int brCitalaca;

		private boolean jeZauzeto(boolean zaPisanje) {
			return (brPisaca > 0) || (zaPisanje && brCitalaca > 0);
		}

		public synchronized void zapocniPisanje() throws InterruptedException {
			while (jeZauzeto(true)) {
				wait();
			}
			brPisaca++;
		}

		public synchronized void zapocniCitanje() throws InterruptedException {
			while (jeZauzeto(false)) {
				wait();
			}
			brCitalaca++;
		}

		public synchronized void zavrsiPisanje() {
			brPisaca--;
			notifyAll();
		}

		public synchronized void zavrsiCitanje() {
			brCitalaca--;
			if (brCitalaca == 0) {
				notify();
			}
		}
	}

	protected final class BazaLock {

		protected Lock brava = new ReentrantLock();
		protected Condition pisci = brava.newCondition();
		protected Condition citaoci = brava.newCondition();

		private int brPisaca;
		private int brCitalaca;

		public void zapocniPisanje() throws InterruptedException {
			brava.lock();
			try {
				while (brPisaca + brCitalaca > 0) {
					pisci.await();
				}
				brPisaca++;
			} finally {
				brava.unlock();
			}
		}

		public void zapocniCitanje() throws InterruptedException {
			brava.lock();
			try {
				while (brPisaca > 0) {
					citaoci.await();
				}
				brCitalaca++;
			} finally {
				brava.unlock();
			}
		}

		public void zavrsiPisanje() {
			brava.lock();
			try {
				brPisaca--;
				pisci.signal();
				citaoci.signalAll();
			} finally {
				brava.unlock();
			}
		}

		public void zavrsiCitanje() {
			brava.lock();
			try {
				brCitalaca--;
				if (brCitalaca == 0) {
					pisci.signalAll();
				}
			} finally {
				brava.unlock();
			}
		}
	}

	protected final class BazaSem {

		protected Semaphore baza = new Semaphore(1);
		protected Semaphore mutex = new Semaphore(1);

		private int brCitalaca;

		public void zapocniPisanje() throws InterruptedException {
			baza.acquire();
		}

		public void zapocniCitanje() throws InterruptedException {
			mutex.acquire();
			try {
				brCitalaca++;
				if (brCitalaca == 1) {
					try {
						baza.acquire();
					} catch (InterruptedException e) {
						brCitalaca--;
						throw e;
					}
				}
			} finally {
				mutex.release();
			}
		}

		public void zavrsiPisanje() {
			baza.release();
		}

		public void zavrsiCitanje() {
			mutex.acquireUninterruptibly();
			try {
				brCitalaca--;
				if (brCitalaca == 0) {
					baza.release();
				}
			} finally {
				mutex.release();
			}
		}
	}

	@AutoCreate(2)
	protected class Pisac extends Thread {

		@Override
		protected void step() {
			radiNestoDrugo();
			try {
				baza.zapocniPisanje();
				try {
					pise();
				} finally {
					baza.zavrsiPisanje();
				}
			} catch (InterruptedException e) {
				stopGracefully();
			}
		}
	}

	@AutoCreate(5)
	protected class Citalac extends Thread {

		@Override
		protected void step() {
			radiNestoDrugo();
			try {
				baza.zapocniCitanje();
				try {
					cita();
				} finally {
					baza.zavrsiCitanje();
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

	protected final Container pisci   = box("Писци").color(MAROON);
	protected final Container citaoci = box("Читаоци").color(NAVY);
	protected final Container resurs  = box("База").color(ROYAL);
	protected final Container main    = column(row(pisci, citaoci), resurs);
	protected final Operation pisac   = init().name("Писац %d").color(ROSE).container(pisci);
	protected final Operation citalac = init().name("Читалац %d").color(AZURE).container(citaoci);
	protected final Operation pisanje = duration("7±2").text("Пише").container(resurs).textAfter("Завршио").update(this::azuriraj);;
	protected final Operation citanje = duration("5±2").text("Чита").container(resurs).textAfter("Завршио").update(this::azuriraj);;
	protected final Operation posao   = duration("6±2").text("Ради").textAfter("Чека");

	protected void pise() {
		pisanje.performUninterruptibly();
	}

	protected void cita() {
		citanje.performUninterruptibly();
	}

	protected void radiNestoDrugo() {
		posao.performUninterruptibly();
	}

	protected void azuriraj(Item item) {
		long brP = resurs.stream(Pisac.class).count();
		long brC = resurs.stream(Citalac.class).count();
		resurs.setText(String.format("%d : %d", brP, brC));
		if (brP == 0 && brC == 0) {
			resurs.setColor(NEUTRAL_GRAY);
		} else if (brP > 0 && brC == 0) {
			resurs.setColor(MAROON);
		} else if (brP == 0 && brC > 0) {
			resurs.setColor(NAVY);
		} else {
			resurs.setColor(ROYAL);
		}
	}

	@Override
	protected void initialize() {
		azuriraj(null);
	}

	public static void main(String[] arguments) {
		launch("Писци и читаоци");
	}
}

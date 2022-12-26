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
 * U frizerskom salonu rade dva berberina. Ako nema musterija, berber sedi u
 * svojoj stolici i spava. Kada musterija udje, ako neki od berbera spava, budi
 * ga, seda za stolicu i berber je sisa. Ako su svi berberi zauzeti, musterija
 * seda za stolicu u cekaonici i ceka da se oslobodi neko od berbera. Kada
 * berber zavrsi sisanje musterije, ako ima musterija koje cekaju, krece da
 * sisa jednu od musterija koje cekaju. Ako nema vise musterija koje cekaju,
 * berber seda u svoju stolicu i spava.
 * 
 * Implementirati sinhronizaciju ove dve vrste procesa kako je opisano.
 */
public class UspavaniBerberi extends Application {

	protected final SalonSem salon = new SalonSem();

	protected final class SalonSync {

		private int brBerbera;
		private int brMusterija;

		public synchronized void cekajMusteriju() throws InterruptedException {
			brBerbera++;
			while (brMusterija == 0) {
				wait();
			}
			brMusterija--;
			notify();
		}

		public synchronized void cekajBerbera() throws InterruptedException {
			brMusterija++;
			while (brBerbera == 0) {
				wait();
			}
			brBerbera--;
			notify();
		}
	}

	protected final class SalonLock {

		protected Lock brava = new ReentrantLock();
		protected Condition imaBerbera = brava.newCondition();
		protected Condition imaMusterija = brava.newCondition();

		private int brBerbera;
		private int brMusterija;

		public void cekajMusteriju() throws InterruptedException {
			brava.lock();
			try {
				brBerbera++;
				while (brMusterija == 0) {
					imaMusterija.await();
				}
				brMusterija--;
				imaBerbera.signal();
			} finally {
				brava.unlock();
			}
		}

		public void cekajBerbera() throws InterruptedException {
			brava.lock();
			try {
				brMusterija++;
				while (brBerbera == 0) {
					imaBerbera.await();
				}
				brBerbera--;
				imaMusterija.signal();
			} finally {
				brava.unlock();
			}
		}
	}

	protected final class SalonSem {

		private Semaphore brBerbera = new Semaphore(0);
		private Semaphore brMusterija = new Semaphore(0);

		public void cekajMusteriju() throws InterruptedException {
			brMusterija.release();
			brBerbera.acquireUninterruptibly();
		}

		public void cekajBerbera() throws InterruptedException {
			brBerbera.release();
			brMusterija.acquireUninterruptibly();
		}
	}

	@AutoCreate(2)
	protected class Berber extends Thread {

		@Override
		protected void step() {
			try {
				salon.cekajMusteriju();
				sisa();
			} catch (InterruptedException e) {
				stopGracefully();
			}
		}
	}

	@AutoCreate
	protected class Musterija extends Thread {

		@Override
		protected void run() {
			try {
				salon.cekajBerbera();
				sisaSe();
			} catch (InterruptedException e) {
				// Samo odlazi
			}
		}
	}

	// ------------------- //
	//    Sistemski deo    //
	// ------------------- //
	// Ne dirati kod ispod //
	// ------------------- //

	protected final Container cekaonica = box("Чекаоница");
	protected final Container stolice   = box("Салон");
	protected final Container main      = column(cekaonica, stolice);
	protected final Operation berber    = init().name("Бербер %d").color(ROSE).text("Спава").container(stolice).update(this::azuriraj);
	protected final Operation musterija = duration("2±1").name("Мушт. %d").color(AZURE).text("Чека").container(cekaonica).update(this::azuriraj);
	protected final Operation sisanjeB  = duration("5").text("Шиша").update(this::azuriraj);
	protected final Operation sisanjeM  = duration("5").text("Шиша се").container(stolice).colorAfter(CHARTREUSE).textAfter("Ошишао се").update(this::azuriraj);

	protected void sisa() {
		sisanjeB.performUninterruptibly();
	}

	protected void sisaSe() {
		sisanjeM.performUninterruptibly();
	}

	protected synchronized void azuriraj(Item item) {
/*		long brB1 = 0;
		long brB2 = 0;
		for (Berber t : stolice.getItems(Berber.class)) {
			if (sisanjeB.getTextBefore().equals(t.getText())) {
				brB1++;
			} else {
				brB2++;
			}
		}*/
		long brB1 = stolice.stream(Berber.class).filter(t -> sisanjeB.getTextBefore().equals(t.getText())).count();
		long brM1 = stolice.stream(Musterija.class).count();
//		long brM2 = cekaonica.stream(Musterija.class).count();
//		cekaonica.setText(String.format("%d", brM2));
		stolice.setText(String.format("%d : %d", brB1, brM1));
		long razlika = brB1 - brM1;
//		if (brB2 > 0 && brM2 > 0) {
//			System.out.println("!!!");
//			cekaonica.setColor(MAROON);
//		} else {
//			cekaonica.setColor(OLIVE);
//		}
		if (razlika != 0) {
			System.out.println("!");
			stolice.setColor(MAROON);
		} else {
			stolice.setColor(ARMY);
		}
	}

	@Override
	protected void initialize() {
		azuriraj(null);
	}

	public static void main(String[] arguments) {
		launch("Успавани бербери");
	}
}

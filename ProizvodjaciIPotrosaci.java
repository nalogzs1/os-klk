package os.examples.classical.gui.solutions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import os.simulation.Application;
import os.simulation.AutoCreate;
import os.simulation.Color;
import os.simulation.Container;
import os.simulation.Item;
import os.simulation.Operation;
import os.simulation.Thread;

/*
 * Dat je bafer fiksne velicine. Vise procesa zeli da istovremeno dodaje i
 * uklanja elemente sa ovog bafera.
 * 
 * Realizovati operaciju dodavanja tako da, ako je bafer pun, blokira proces
 * dok se ne oslobodi mesto za novi element. Takodje, realizovati operaciju
 * uklanjanja tako da, ako je bafer prazam, blokira proces dok se ne doda novi
 * element. 
 */
public class ProizvodjaciIPotrosaci extends Application {

	protected class BaferSync extends Bafer {

		public BaferSync(int velicina) {
			super(velicina);
		}

		private void cekajDokIma(int br) {
			boolean interrupted = Thread.interrupted();
			while (lista.size() == br) {
				try {
					wait();
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public synchronized void stavi(Element o) {
			cekajDokIma(velicina);
			super.stavi(o);
			notify();
		}

		@Override
		public synchronized Element uzmi() {
			cekajDokIma(0);
			Element result = super.uzmi();
			notify();
			return result;
		}
	}

	protected class BaferLock extends Bafer {

		protected Lock brava = new ReentrantLock();
		protected Condition imaPunih = brava.newCondition();
		protected Condition imaPraznih = brava.newCondition();

		public BaferLock(int velicina) {
			super(velicina);
		}

		@Override
		public void stavi(Element o) {
			brava.lock();
			try {
				while (lista.size() == velicina) {
					imaPraznih.awaitUninterruptibly();
				}
				super.stavi(o);
				imaPunih.signal();
			} finally {
				brava.unlock();
			}
		}

		@Override
		public Element uzmi() {
			brava.lock();
			try {
				while (lista.size() == 0) {
					imaPunih.awaitUninterruptibly();
				}
				Element result = super.uzmi();
				imaPraznih.signal();
				return result;
			} finally {
				brava.unlock();
			}
		}
	}

	protected class BaferSem extends Bafer {

		protected Semaphore mutex = new Semaphore(1);
		protected Semaphore imaPunih;
		protected Semaphore imaPraznih;

		public BaferSem(int velicina) {
			super(velicina);
			imaPunih = new Semaphore(0);
			imaPraznih = new Semaphore(velicina);
		}

		@Override
		public void stavi(Element o) {
			imaPraznih.acquireUninterruptibly();
			mutex.acquireUninterruptibly();
			try {
				super.stavi(o);
			} catch (Exception e) {   // U slucaju greske, vrati vrednost
				imaPraznih.release(); // semafora posto nismo uspesno stavili
				throw e;              // element, mesto je i dalje slobodno
			} finally {
				mutex.release();
			}
			imaPunih.release();
		}

		@Override
		public Element uzmi() {
			imaPunih.acquireUninterruptibly();
			mutex.acquireUninterruptibly();
			try {
				Element result = super.uzmi();
				imaPraznih.release();
				return result;
			} catch (Exception e) { // Analogno kao i u slucaju stavljanja
				imaPunih.release();
				throw e;
			} finally {
				mutex.release();
			}
		}
	}

	protected Bafer bafer = new BaferSem(12);
	protected class Bafer {

		protected final List<Element> lista = new ArrayList<>();
		protected final int velicina;

		public Bafer(int velicina) {
			this.velicina = velicina;
		}

		public void stavi(Element o) {
			lista.add(o);
			elementi.addItem(o);
		}

		public Element uzmi() {
			Element result = lista.remove(0);
			elementi.removeItem(result);
			return result;
		}
	}

	// ------------------- //
	//    Sistemski deo    //
	// ------------------- //
	// Ne dirati kod ispod //
	// ------------------- //

	@AutoCreate(4)
	protected class Proizvodjac extends Thread {

		private final int id = getID();
		private int br = 0;

		@Override
		protected void step() {
			Element element = proizvedi(id + "x" + (br++));
			bafer.stavi(element);
		}

	}

	@AutoCreate(4)
	protected class Potrosac extends Thread {

		@Override
		protected void step() {
			Element element = bafer.uzmi();
			potrosi(element);
		}
	}

	protected final Container proizvodjaci = box("Произвођачи").color(NAVY);
	protected final Container potrosaci    = box("Потрошачи").color(MAROON);
	protected final Container elementi     = box("Елементи").color(NEUTRAL_GRAY);
	protected final Container main         = row(proizvodjaci, elementi, potrosaci);
	protected final Operation proizvodjac  = init().name("Произв. %d").color(AZURE).text("Чека").container(proizvodjaci);
	protected final Operation potrosac     = init().name("Потр. %d").color(ROSE).text("Чека").container(potrosaci);
	protected final Operation proizvodnja  = duration("3±1").text("Производи").textAfter("Чека");
	protected final Operation potrosnja    = duration("7±2").text("Троши %s").textAfter("Чека");

	protected Element proizvedi(String vrednost) {
		proizvodnja.performUninterruptibly();
		return new Element(vrednost);
	}

	protected void potrosi(Element element) {
		potrosnja.performUninterruptibly(element.getName());
	}

	protected class Element extends Item {

		public Element(String vrednost) {
			setName(vrednost);
		}

		private int getIndex() {
			return bafer.lista.indexOf(this);
		}

		@Override
		public Color getColor() {
			int index = getIndex();
			if ((index >= 0) && (index < bafer.velicina)) {
				return CHARTREUSE;
			} else {
				return ORANGE;
			}
		}

		@Override
		public String getText() {
			return String.format("Bafer[%d]", getIndex());
		}
	}

	public static void main(String[] arguments) {
		launch("Произвођачи и потрошачи");
	}
}

package compression;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * CodageZTW est une classe qui permet d'effectuer un codage/decodage binaire
 * d'une decomposition en ondelettres.
 * 
 * <p>
 * Codage binaire ZTW d'une decoposition multiresolution en ondelettes. Zero
 * Tree Wavelet Coding.
 * </p>
 * 
 * @author Cedric Golmard
 * @version 1.0
 */
public abstract class CodageZTW {

	/**
	 * Code binaire des classes de pixels.
	 * 
	 * Remarque : le premier boolean est false si non significatif, true si
	 * significatif
	 */

	/**
	 * Code binaire d'un pixel Zero-Tree Root
	 */
	public final static boolean[] ZTR = { false, false };

	/**
	 * Code binaire d'un pixel Zero-Isolated
	 */
	public final static boolean[] ZI = { false, true };

	/**
	 * Code binaire d'un pixel Positive
	 */
	public final static boolean[] P = { true, true };

	/**
	 * Code binaire d'un pixel Negative
	 */
	public final static boolean[] N = { true, false };

	/**
	 * Code pour un pixel descendant d'un ZTR
	 */
	public final static boolean[] NS = { false, false, false };

	/**
	 * Codage ZTW d'une image transformee.
	 * <p>
	 * Le critere d'arret du codage peut etre le niveau de resolution ou la
	 * taille du fichier.
	 * </p>
	 * 
	 * @param xt
	 *            image transformee a coder
	 * @param width
	 *            taille de l'image
	 * @param height
	 *            taile de l'image
	 * @param niv_resol
	 *            nombre de niveaux de resolution
	 * @param size
	 *            taille imposee du flux binaire en (kbits)
	 * @param bitstream_name
	 *            nom du fichier de stockage du flux binaire
	 * 
	 * @return entier informant de la reussite ou non du codage
	 * @throws IOException
	 */
	// condition : niv-resol < sqrt(height)
	public static int ztw_code(double[][] xt, int width, int height, int niv_resol,
			int size, String bitstream_name) throws IOException {
		/**
		 * Initialisation
		 */
		int M = (int) (height / Math.pow(2, niv_resol - 1));
		int N = (int) (width / Math.pow(2, niv_resol - 1));
		double T = seuil(xt);
		boolean[][][] etiquettes = new boolean[height][width][];
		int current_size = 0;
		DataOutputStream ecrivain = new DataOutputStream(
				new BufferedOutputStream(new FileOutputStream(
						bitstream_name)));
		/**
		 * 
		 */
		ecrivain.writeDouble(T);
		
		/**
		 * Iterations de l'algorithme
		 */
		while (current_size < size) {
			/**
			 *  Sous-bande basses frequences
			 */
			M = (int) (height / Math.pow(2, niv_resol - 1));
			N = (int) (width / Math.pow(2, niv_resol - 1));
			for (int i = 0; i < M; i++) {
				for (int j = 0; j < N; j++) {
					determinerEtiquette(xt, etiquettes, i, j, T, niv_resol,
							height, width);
					ecrivain.writeBoolean(etiquettes[i][j][0]);
					ecrivain.writeBoolean(etiquettes[i][j][1]);
				}
			}

			/**
			 *  Sous-bande hautes frequences
			 */
			M = (int) (height / Math.pow(2, niv_resol - 1));
			N = (int) (width / Math.pow(2, niv_resol - 1));
			while (M <= height && N <= width) {
				for (int i = 0; i < M; i++)
					for (int j = N; j < 2 * N; j++) { // Sous-bande 1
						determinerEtiquette(xt, etiquettes, i, j, T, niv_resol,
								height, width);
						ecrivain.writeBoolean(etiquettes[i][j][0]);
						ecrivain.writeBoolean(etiquettes[i][j][1]);
					}
				for (int i = M; i < 2 * M; i++)
					for (int j = 0; j < N; j++) { // Sous-bande 2
						determinerEtiquette(xt, etiquettes, i, j, T, niv_resol,
								height, width);
						ecrivain.writeBoolean(etiquettes[i][j][0]);
						ecrivain.writeBoolean(etiquettes[i][j][1]);
					}
				for (int i = M; i < 2 * M; i++)
					for (int j = N; j < 2 * N; j++) { // Sous-bande 3
						determinerEtiquette(xt, etiquettes, i, j, T, niv_resol,
								height, width);
						ecrivain.writeBoolean(etiquettes[i][j][0]);
						ecrivain.writeBoolean(etiquettes[i][j][1]);
					}
				M *= 2;
				N *= 2;
			}

			/**
			 *  Actualisation des coefficients de l'image
			 */
			current_size = 1000 * ecrivain.size();
			T /= 2;
			actualiseCoeff(xt, etiquettes, T, height, width);
			// Flush : écrit cette partie dans le fichier, au cas ou ça bloque avant on a toujours cette partie de codée.
			ecrivain.flush();
		}
		
		/**
		 * Fermeture du fichier et fin.
		 */
		ecrivain.close();
		return 0;
	}


	/**
	 * Decodage d'un flux binaire ZWTC
	 * <p>
	 * </p>
	 * 
	 * @param xtrec
	 *            image transformee reconstruite a partir du flux bianire
	 * @param width
	 *            taille de l'image
	 * @param height
	 *            taile de l'image
	 * @param niv_resol
	 *            nombre de niveaux de resolution
	 * @param bitstream_name
	 *            nom du fichier de stockage du flux binaire
	 * 
	 * @return entier informant de la reussite ou non du decodage
	 * @throws IOException 
	 */
	public static int ztw_decode(double[][] xtrec, int width, int height,
			int niv_resol, String bitstream_name) throws IOException {
		/**
		 * 
		 */
		boolean[][][] etiquettes = null;
		double T = 0;
		int N, M;
		for(int i = 0; i < height; i++ )
			for(int j = 0; j < width; j++ )
				xtrec[i][j] = 0;
		/**
		 * Ouverture du fichier
		 */
		DataInputStream dis = null;
		dis = new DataInputStream(new FileInputStream(new
				File(bitstream_name)));
		
		/**
		 * Lecture du seuil initial
		 */
		T = dis.readDouble();
		
		/**
		 * Iteration
		 */
		while(dis.available() > 0) {
			etiquettes = null;
			/**
			 *  Sous-bande basses frequences
			 */
			M = (int) (height / Math.pow(2, niv_resol - 1));
			N = (int) (width / Math.pow(2, niv_resol - 1));
			for (int i = 0; i < M; i++) {
				for (int j = 0; j < N; j++) {
					readEtiquetteFromBitstream(etiquettes, width, height, niv_resol, i, j, dis);
				}
			}

			/**
			 *  Sous-bande hautes frequences
			 */
			M = (int) (height / Math.pow(2, niv_resol - 1));
			N = (int) (width / Math.pow(2, niv_resol - 1));
			while (M <= height && N <= width) {
				for (int i = 0; i < M; i++)
					for (int j = N; j < 2 * N; j++) { // Sous-bande 1
						readEtiquetteFromBitstream(etiquettes, width, height, niv_resol, i, j, dis);
					}
				for (int i = M; i < 2 * M; i++)
					for (int j = 0; j < N; j++) { // Sous-bande 2
						readEtiquetteFromBitstream(etiquettes, width, height, niv_resol, i, j, dis);
					}
				for (int i = M; i < 2 * M; i++)
					for (int j = N; j < 2 * N; j++) { // Sous-bande 3
						readEtiquetteFromBitstream(etiquettes, width, height, niv_resol, i, j, dis);
					}
				M *= 2;
				N *= 2;
			}

			/**
			 *  Actualisation des coefficients de l'image
			 */
			for(int i = 0; i < height; i++ )
				for(int j = 0; j < width; j++ )
					actualiseCoeff(xtrec, etiquettes, T, height, width);
			/**
			 * Actualisation du seuil
			 */
			T /= 2;
		}
		
		dis.close();
		return 0;
	}
	
	/**
	 * 
	 * @param etiquettes
	 * @param width
	 * @param height
	 * @param niv_resol
	 * @param i
	 * @param j
	 * @param dis
	 * @throws IOException
	 */
	static void readEtiquetteFromBitstream(boolean[][][] etiquettes, int width, int height, int niv_resol, int i, int j, DataInputStream dis) throws IOException{
		/**
		 * Cas 1 : etiquette deja connue
		 */
		if(etiquettes[i][j] == NS)
			return;
		if(etiquettes[i][j] == P || etiquettes[i][j] == N || etiquettes[i][j] == ZI || etiquettes[i][j] == ZTR)
			return;
		
		/**
		 * Cas general : lecture de l'etiquette dans le fichier
		 */
		etiquettes[i][j][0] = dis.readBoolean();
		etiquettes[i][j][1] = dis.readBoolean();
		
		/**
		 * Verification de la descendance si ZTR
		 */
		if(etiquettes[i][j] == ZTR){
			marquerDescendantsNS(etiquettes, i, j, niv_resol, height, width);
		}
	}
	
	
	/**
	 * Calcul de la valeur de seuil initiale.
	 * <p>
	 * La valeur initiale de seuil est la moitie de la plus grande valeur (en
	 * valeur absolue) de l'image transformee.
	 * </p>
	 * 
	 * @param donnee
	 *            image transformee dans un tableau de double
	 * 
	 * @return valeur de seuil initiale
	 */
	private static double seuil(double[][] donnee) {
		double max_temp = -1;
		for (int i = 0; i < donnee.length; i++)
			for (int j = 0; j < donnee[i].length; j++)
				if (max_temp < Math.abs(donnee[i][j]))
					max_temp = Math.abs(donnee[i][j]);
		return max_temp / 2;
	}

	/**
	 * Actualise les coefficients d'un pixel significatif selon son signe
	 * 
	 * @param donnee
	 *            image transformee
	 * @param etiquettes
	 *            tableau auxiliaire qui code si un pixel est significatif ou
	 *            non
	 * @param i
	 * @param j
	 *            coordonnees du coefficient
	 * @param seuil
	 *            valeur de seuil
	 * 
	 * @return nouvelle valeur du coefficient
	 */
	private static void actualiseCoeff(double donnee[][], boolean[][][] etiquettes,
			double seuil, int height, int width) {
		for (int i = 0; i < height; i++) {
			for (int j = 0; j < width; j++) {
				if (etiquettes[i][j].equals(P))
					donnee[i][j] -= seuil;
				else if (etiquettes[i][j].equals(N))
					donnee[i][j] += seuil;
			}
		}

	}

	/**
	 * Determne l'etiquette associee au pixel de l'image donnee.
	 * 
	 * @param x
	 * @param etiquettes
	 * @param i
	 * @param j
	 * @param seuil
	 * @param niv_resol
	 * @param height
	 * @param width
	 */
	private static void determinerEtiquette(double[][] x, boolean[][][] etiquettes,
			int i, int j, double seuil, int niv_resol, int height, int width) {

		if (etiquettes[i][j].length >= 2) { // etiquette deja connue
			// Rien a faire =)
		} else if (Math.abs(x[i][j]) > seuil) { // pixel significatif
			if (x[i][j] >= 0) {
				etiquettes[i][j] = P;
			} else {
				etiquettes[i][j] = N;
			}
		} else { // pixel non-significatif
			int M = (int) (height / Math.pow(2, niv_resol - 1));
			int N = (int) (width / Math.pow(2, niv_resol - 1));
			if ((i < M) | (j < N)) { // sous-bande basse frequence
				// Appel recursif
				determinerEtiquette(x, etiquettes, i, j + N, seuil, niv_resol,
						height, width);
				determinerEtiquette(x, etiquettes, i + M, j, seuil, niv_resol,
						height, width);
				determinerEtiquette(x, etiquettes, i + M, j + N, seuil,
						niv_resol, height, width);

				// Comparaison
				if (etiquettes[i][j + N][0] == true
						| etiquettes[i][j + N][0] == true
						| etiquettes[i][j + N][0] == true) { // Pixel ZI
					etiquettes[i][j] = ZI;
				} else {
					etiquettes[i][j] = ZTR;
					marquerDescendantsNS(etiquettes, i, j, niv_resol, height,
							width);
				}
			} else { // sous-bande haute frequence
				// Appel recursif
				determinerEtiquette(x, etiquettes, 2 * i, 2 * j, seuil,
						niv_resol, height, width);
				determinerEtiquette(x, etiquettes, 2 * i, 2 * j + 1, seuil,
						niv_resol, height, width);
				determinerEtiquette(x, etiquettes, 2 * i + 1, 2 * j, seuil,
						niv_resol, height, width);
				determinerEtiquette(x, etiquettes, 2 * i + 1, 2 * j + 1, seuil,
						niv_resol, height, width);

				// Comparaison
				if (etiquettes[2 * i][2 * j][0] == true
						| etiquettes[2 * i + 1][2 * j][0] == true
						| etiquettes[2 * i][2 * j + 1][0] == true
						| etiquettes[2 * i + 1][2 * j + 1][0] == true) { // Pixel
																			// ZI
					etiquettes[i][j] = ZI;
				} else {
					etiquettes[i][j] = ZTR;
					marquerDescendantsNS(etiquettes, i, j, niv_resol, height,
							width);
				}
			}
		}

	}

	/**
	 * Marque tous les descendants du pixel comme NS : a en pas balayer
	 * 
	 * @param etiquettes
	 * @param i
	 * @param j
	 * @param niv_resol
	 * @param height
	 * @param width
	 */
	private static void marquerDescendantsNS(boolean[][][] etiquettes, int i, int j,
			int niv_resol, int height, int width) {
		if (i >= height | j >= width) // Critere d'arret bords de l'image
			return;
		int M = (int) (height / Math.pow(2, niv_resol - 1));
		int N = (int) (width / Math.pow(2, niv_resol - 1));
		if ((i < M) | (j < N)) { // sous-bande basse frequence
			// Etiquetage en ZNS des descendants directs
			etiquettes[i + M][j] = NS;
			etiquettes[i][j + N] = NS;
			etiquettes[i + M][j + N] = NS;

			// Appel recursif
			marquerDescendantsNS(etiquettes, i + M, j, niv_resol, height, width);
			marquerDescendantsNS(etiquettes, i, j + N, niv_resol, height, width);
			marquerDescendantsNS(etiquettes, i + M, j + N, niv_resol, height,
					width);
		} else { // sous-bande haute frequence
			// Etiquetage en ZNS des descendants directs
			etiquettes[2 * i][2 * j] = NS;
			etiquettes[2 * i + 1][2 * j] = NS;
			etiquettes[2 * i][2 * j + 1] = NS;
			etiquettes[2 * i + 1][2 * j + 1] = NS;

			// Appel recursif
			marquerDescendantsNS(etiquettes, 2 * i, 2 * j, niv_resol, height,
					width);
			marquerDescendantsNS(etiquettes, 2 * i + 1, 2 * j, niv_resol,
					height, width);
			marquerDescendantsNS(etiquettes, 2 * i, 2 * j + 1, niv_resol,
					height, width);
			marquerDescendantsNS(etiquettes, 2 * i + 1, 2 * j + 1, niv_resol,
					height, width);
		}
	}

	// FAUX MAIS ON GARDE AU CAS OU.
	// /**
	// * Effectue un balayage de l'image transformee selon l'ordre de parcours
	// des
	// * sous-bande.
	// *
	// * @param donnee
	// * image transformee
	// * @param etiquettes
	// * tableau auxiliaire qui code si un pixel est significatif ou
	// * non
	// * @param seuil
	// * valeur de seuil
	// *
	// */
	// private void balayage(double[][] donnee, boolean[][][] etiquettes,
	// int niv_resol, double seuil) {
	// // Taille de l'image = 2^t
	// int t = (int) Math.sqrt(donnee.length);
	// // Premiere sous-bande
	// for (int u = 0; u < (int) Math.pow(2, t - niv_resol); u++)
	// for (int v = 0; v < (int) Math.pow(2, t - niv_resol); v++) {
	// // Les pixels ZTR ne sont pas balayes
	// if (etiquettes[u][v] == NB)
	// continue;
	// else {
	// // Determination de la etiquettes du pixel :
	// // singificatif P, N ou non.
	// if (Math.abs(donnee[u][v]) > seuil) {
	// if (donnee[u][v] < 0)
	// etiquettes[u][v] = N;
	// else
	// etiquettes[u][v] = P;
	// } else
	// etiquettes[u][v] = NS;
	// }
	// }
	//
	// // Balayage iteratif
	// for (int n = (int) Math.pow(2, t - niv_resol); n < donnee.length; n *= 2)
	// {
	// // sous-bande 1
	// rasterscan(donnee, etiquettes, 1, n, seuil);
	// // sous-bande 2
	// rasterscan(donnee, etiquettes, n, 1, seuil);
	// // sous-bande 3
	// rasterscan(donnee, etiquettes, n, n, seuil);
	// }
	// }
	//
	// /**
	// * Effectue un balayge raster-scan des coefficients d'une sous-bande
	// *
	// * @param donnee
	// * image transformee
	// * @param etiquettes
	// * tableau auxiliaire qui code si un pixel est significatif ou
	// * non
	// * @param i
	// * premiere coordonnee du premier pixel de la sous-bande
	// * @param j
	// * deuxieme coordonnee du deuxieme pixel de la sous-bande
	// * @param seuil
	// * valeur de seuil de comparaison
	// */
	// private void rasterscan(double[][] donnee, boolean[][][] etiquettes,
	// int i, int j, double seuil) {
	// int taille = Math.max(i, j);
	//
	// for (int u = i; u < taille; u++) {
	// for (int v = i; v < taille; v++) {
	// // Les pixels ZTR ne sont pas balayes
	// if (etiquettes[u][v] == ZTR) {
	// continue;
	// } else {
	// // Determination de la etiquettes du pixel :
	// // singificatif P, N ou non.
	// if (Math.abs(donnee[u][v]) < seuil) {
	// if (donnee[u][v] < 0)
	// etiquettes[u][v] = N;
	// else
	// etiquettes[u][v] = P;
	// } else {
	// etiquettes[u][v] = NS;
	// }
	// }
	// }
	// }
	// }
	//
	// /**
	// *
	// * @param etiquettes
	// * @param i
	// * @param j
	// * @param height
	// * @param width
	// * @param niv_resol
	// */
	// private void getetiquettes(boolean[][][] etiquettes, int i, int j,
	// int height, int width, int niv_resol) {
	// // Cas de sortie : pixel deja classe
	// if (etiquettes[i][j].length == 2)
	// return;
	// if (etiquettes[i][j] == NB)
	// return;
	//
	// // Cas initial : Sous-bandes extremes pour pixels non significatifs
	// // Les pixels n'ont pas de descendants
	// if (i > height / 2 | j > width / 2) {
	// etiquettes[i][j] = ZTR;
	// return;
	// }
	//
	// // Cas initial : Sous-bande 0 et pixel non significatif
	// if (i < height / Math.pow(2, niv_resol - 1)
	// | j < width / Math.pow(2, niv_resol - 1)) {
	// // on regare les descendants
	// int N = (int) Math.pow(2, (int) Math.sqrt(height) - niv_resol);
	// getetiquettes(etiquettes, i + N, j, height, width,
	// niv_resol);
	// getetiquettes(etiquettes, i, j + N, height, width,
	// niv_resol);
	// getetiquettes(etiquettes, i + N, j + N, height, width,
	// niv_resol);
	// boolean[] fils1 = etiquettes[i + N][j];
	// boolean[] fils2 = etiquettes[i][j + N];
	// boolean[] fils3 = etiquettes[i + N][j + N];
	// boolean[] fils4 = NS; // ajout fictif pour reutilser le code suivant
	//
	// if (fils1[0] == true | fils2[0] == true | fils3[0] == true
	// | fils4[0] == true) {
	// etiquettes[i][j] = ZI;
	// return;
	// } else {
	// // mise a jour de la etiquettes
	// etiquettes[i][j] = ZTR;
	// // marquage des enfants
	// etiquettes[i + N][j] = NB;
	// etiquettes[i][j + N] = NB;
	// etiquettes[i + N][j + N] = NB;
	// return;
	// }
	// }
	// // Cas general : pixel non significatif et pas encore classe
	// // on regarde les descendants de facon recursive
	// getetiquettes(etiquettes, 2 * i, 2 * j, height, width,
	// niv_resol);
	// getetiquettes(etiquettes, 2 * i + 1, 2 * j, height, width,
	// niv_resol);
	// getetiquettes(etiquettes, 2 * i, 2 * j + 1, height, width,
	// niv_resol);
	// getetiquettes(etiquettes, 2 * i + 1, 2 * j + 1, height, width,
	// niv_resol);
	// boolean[] fils1 = etiquettes[2 * i][2 * j];
	// boolean[] fils2 = etiquettes[2 * i + 1][2 * j];
	// boolean[] fils3 = etiquettes[2 * i][2 * j + 1];
	// boolean[] fils4 = etiquettes[2 * i + 1][2 * j + 1];
	//
	// if (fils1[0] == true | fils2[0] == true | fils3[0] == true
	// | fils4[0] == true) {
	// etiquettes[i][j] = ZI;
	// return;
	// } else {
	// // mise a jour de la etiquettes
	// etiquettes[i][j] = ZTR;
	// // marquage des enfants
	// etiquettes[2 * i][2 * j] = NB;
	// etiquettes[2 * i][2 * j] = NB;
	// etiquettes[2 * i][2 * j] = NB;
	// etiquettes[2 * i][2 * j] = NB;
	// return;
	// }
	//
	// }
	//
	// /*
	// private void updateBitstream(boolean[][] bistream, boolean[][][]
	// etiquettes, int width, int height, int niv_resol){
	// // Premiere sous-bande
	// int t = (int) Math.pow(2, t - niv_resol)
	// for (int u = 0; u < t; u++)
	// for (int v = 0; v < (int) Math.pow(2, t - niv_resol); v++) {
	// }
	//
	// // Balayage iteratif
	// for (int n = t; n < width; n *= 2) {
	// // Sous-bande 1
	// for (int u = 0; u < t; u++) {
	// for (int v = t; v < 2*t; v++) {
	//
	// }
	// }
	// // Sous-bande 2
	// for (int u = t; u < 2*t; u++) {
	// for (int v = 0; v < t; v++) {
	//
	// }
	// }
	// // Sous-bande 3
	// for (int u = t; u < 2*t; u++) {
	// for (int v = t; v < 2*t; v++) {
	//
	// }
	// }
	// }
	// }
	// */

}

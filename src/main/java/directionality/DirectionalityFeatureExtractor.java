/*
 * @module: Core feature extraction module
 * @author: Naveen Ashish
 * @contact: naveen.ashish@gmail.com
 * @lastupdated: 07/08/2014
 */

package directionality;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

import utilities.IO;
import utilities.SentenceLitHarmReplacer;
import utlities.nlp.NLParser;

public class DirectionalityFeatureExtractor {

	private List<String> stkAnywhere = new ArrayList<String>();

	private List<String> stkBegin = new ArrayList<String>();

	private List<String> stkCause = new ArrayList<String>();

	private HashMap hmLitHarm = new HashMap();

	private HashMap hmTell = new HashMap();

	private HashMap hmDirectionality = new HashMap();

	private HashMap hmTailInd = new HashMap();

	private HashMap hmSentenceScore = new HashMap();

	private NLParser nlParser = new NLParser();

	private Vector strTraining = new Vector();

	private Vector strIDs = new Vector();

	private FeatureFunctionsLibrary ffl = new FeatureFunctionsLibrary();

	private SentenceLitHarmReplacer slhr = new SentenceLitHarmReplacer();

	private int startPoint = 0;

	private IO io = new IO();

	/*
	 * @functionality: Read in the manually created lexicons in different
	 * categories
	 * 
	 * @notes: Lexicons contain indicative words and phrases
	 */
	private void initialize() {

		stkAnywhere = io.readFileStk("lexicons/anywhere.txt");

		stkBegin = io.readFileStk("lexicons/begin.txt");

		stkCause = io.readFileStk("lexicons/cause.txt");

		nlParser.initialize();
	}

	/*
	 * @functionality: Read in specified Litagion and Harm for each PMID
	 */
	private void readLitHarm() {

		List<String> stk = io.readFileStk("resources/Training.txt");

		for (int i = stk.size() - 1; i > 0; i--) {
			String str = stk.get(i);

			String[] parts = str.split("\t");

			if (parts.length > 2) {

				String ID = parts[0].trim();

				String litharm = parts[1].trim() + " : " + parts[2].trim();

				hmLitHarm.put(ID, litharm);
			}
		}
	}

	/*
	 * @param tellFname Name of file with abstracts and tell sentences
	 * identified in each abstract
	 * 
	 * @functionality For each abstract, take all tell sentences and process all
	 * tell sentences to generate feature vector for abstract
	 */
	private void processTell(String tellFname) {

		// Read in tell sentences
		List<String> stk = io.readFileStk(tellFname);

		int S = stk.size();

		List<String> stkTell = new ArrayList<String>();

		List<String> stkAll = new ArrayList<String>();

		String key = "", lit = "<<<<<<<<<<<", harm = "<<<<<<<<<<<";

		for (int i = 0; i < S - 1; ++i) {

			String sent = stk.get(i);

			if (sent.indexOf("------ ") > -1) {
				// Marks a new abstract in the tell sentences file

				if (stkTell.isEmpty()) {
					stkTell.addAll(stkAll);
				}

				hmTell.put(key, stkTell);

				// System.out.println(key+" :: "+stkTell.size());

				key = sent;

				stkTell = new ArrayList<String>();

				stkAll = new ArrayList<String>();

				String litharm = stk.get(i + 1);

				String[] arr = getLitHarm(litharm);

				lit = arr[0];

				harm = arr[1];

				hmLitHarm.put(key, litharm);

				// System.out.println(key+" :"+litharm);

			}

			if (sent.indexOf("--->") > -1) {
				// This indicates a tell sentence
				try {

					if (lit.indexOf("|") < 0) {
						// No variants for the litagion name

						sent = slhr.tagIndicatorsBased(sent, harm, lit);

					} else {
						// We do have variants for the litagion name !

						String[] parts = lit.split("[\\|]");

						String lit1 = parts[0].trim();
						String lit2 = parts[1].trim();

						sent = slhr.tagIndicatorsBased(sent, harm, lit);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				try {

					sent = sent.replaceAll(harm, "SPECHARM");

				} catch (Exception e) {
				}

				stkTell.add(sent);
			} else {
				// Just a regular NON tell sentence

				stkAll.add(sent);
			}
		}
	}

	/*
	 * @param tellFname Name of file with abstracts and tell sentences
	 * identified in each abstract
	 * 
	 * @functionality: For each abstract, take all tell sentences and process
	 * all tell sentences to generate feature vector for abstract
	 */
	private void processTellNew(String tellFname) {

		// Read in tell sentences
		List<String> stk = io.readFileStk(tellFname);

		int S = stk.size();

		List<String> stkTell = new ArrayList<String>();
		List<String> stkAll = new ArrayList<String>();

		String key = "", lit = "<<<<<<<<<<<", harm = "<<<<<<<<<<<";

		for (int i = 0; i < S - 1; ++i) {

			String[] parts = stk.get(i).split("\t");

			if (parts.length > 4) {

				key = parts[1] + parts[2] + parts[3];

				String directionality = parts[0].trim();

				hmDirectionality.put(key, directionality);

				String[] tells = parts[4].split("\\|");

				int T = tells.length;

				// stkTell.clear();

				stkTell = new Stack();

				for (int j = 0; j < T; ++j) {

					System.out.println(tells[j]);
					stkTell.add(tells[j]);
				}

				hmTell.put(key, stkTell);

				lit = parts[2];

				harm = parts[3];

				hmLitHarm.put(key, lit + ":" + harm);

			}
		}
	}

	/*
	 * @param litharm is a string with litagion and harm separated by :
	 * 
	 * @return String litagion and harm, separated as in array
	 */
	private String[] getLitHarm(String litharm) {

		String[] parts = litharm.split(" : ");

		String[] arr = new String[2];

		arr[0] = parts[0].trim();
		arr[1] = parts[1].trim();

		return arr;
	}

	/*
	 * @param: tellsent is a "marked up" tell sentence
	 * 
	 * @return: a code [0-7] indicating the type of tell sentence
	 */

	private int getSentenceTypeCode(String tellsent) {

		int summ = 0, lit = 0, harm = 0;

		// if (tellsent.indexOf("SUMM")>-1) summ=1;

		if (isSummary(tellsent)) {

			// System.out.println("SUMM");
			summ = 1;
		}

		if ((tellsent.indexOf("SPEC_LIT") > -1))
			lit = 1;

		if ((tellsent.indexOf("SPEC_HARM") > -1))
			harm = 1;

		int code = (summ * 4) + (lit * 2) + harm;

		// System.out.println("CODE: "+code);

		code = 7;

		return code;
	}

	/*
	 * @functionality: get the feature vector for a complete abstract
	 * 
	 * @param: key is the abstract PMID
	 * 
	 * @param: stkTell is a stack of all tell sentences for the abstract
	 * 
	 * @return: feature vector (comma separated string) for abstract
	 */

	private String getFeatureVectorAbstract(String key, Stack stkTell) {

		Double numNegated = 0.0, numIndNegated = 0.0, numCauseNegated = 0.0, numTrigger = 0.0;

		Double[] numNegatedArr = new Double[10];

		Double[] numIndNegatedArr = new Double[10];

		Double[] numCauseNegatedArr = new Double[10];

		Double[] numTriggerArr = new Double[10];

		// Double aLength = new Double(stkTell.size());

		Double fracNegated = 0.0, fracTrigger = 0.0;

		Double[] fracNegatedArr = new Double[10];

		Double[] fracTriggerArr = new Double[10];

		// Initialize
		for (int i = 0; i < 10; ++i) {

			numNegatedArr[i] = 0.0;

			numIndNegatedArr[i] = 0.0;

			numCauseNegatedArr[i] = 0.0;

			numTriggerArr[i] = 0.0;

			fracNegatedArr[i] = 0.0;

			fracTriggerArr[i] = 0.0;
		}

		String absFeatureVec = "";

		while (!stkTell.isEmpty()) {

			String sentence = stkTell.pop().toString();

			// System.out.println("PROC: "+sentence);

			int code = getSentenceTypeCode(sentence);

			// System.out.println(code+" "+sentence);

			int ind = sentence.indexOf("--->");

			if (ind > -1)
				sentence = sentence.substring(ind + 4);

			/*
			 * Get the feature vector for each (tell) sentence in abstract and
			 * aggregate
			 */
			String fV = getFeatureVectorSentence(sentence);

			String[] featureVec = fV.split("\t");

			if (featureVec[0].equals("T")) {

				++numNegated;

				++numNegatedArr[code];
			}

			if (featureVec[1].equals("T")) {

				++numIndNegated;

				++numIndNegatedArr[code];
			}

			if (featureVec[2].equals("T")) {

				++numCauseNegated;

				++numCauseNegatedArr[code];
			}

			if (featureVec[3].equals("T")) {

				++numTrigger;

				++numTriggerArr[code];
			}
		}

		if ((numNegated + numTrigger) > 0) {

			fracNegated = Double.parseDouble(numNegated.toString())
					/ (Double.parseDouble(numNegated.toString()) + Double.parseDouble(numTrigger.toString()));

			fracTrigger = Double.parseDouble(numTrigger.toString())
					/ (Double.parseDouble(numNegated.toString()) + Double.parseDouble(numTrigger.toString()));

		}

		for (int i = 0; i < 8; ++i) {

			if ((numNegatedArr[i] + numTriggerArr[i]) > 0) {

				fracNegatedArr[i] = Double.parseDouble(numNegatedArr[i].toString())
						/ (Double.parseDouble(numNegatedArr[i].toString()) + Double.parseDouble(numTriggerArr[i]
								.toString()));

				fracTriggerArr[i] = Double.parseDouble(numTriggerArr[i].toString())
						/ (Double.parseDouble(numNegatedArr[i].toString()) + Double.parseDouble(numTriggerArr[i]
								.toString()));
			}
		}

		/*
		 * Collate over all (8) tell sentence types
		 */
		for (int i = 0; i < 8; ++i) {

			absFeatureVec = absFeatureVec + numNegatedArr[i] + "," + numIndNegatedArr[i] + "," + numCauseNegatedArr[i]
					+ "," + numTriggerArr[i] + "," + fracNegatedArr[i].toString() + "," + fracTriggerArr[i].toString()
					+ ",";
		}

		absFeatureVec = absFeatureVec + numNegated + "," + numIndNegated + "," + numCauseNegated + "," + numTrigger
				+ "," + fracNegated.toString() + "," + fracTrigger.toString();

		// System.out.println(absFeatureVec+","+getKey(key));

		System.out.println(absFeatureVec + "," + hmDirectionality.get(key));

		// if (!key.trim().equals(""))
		// strTraining.add(absFeatureVec+","+getKey(key)+","+key);

		if (!key.trim().equals("")) {

			strTraining.add(absFeatureVec + "," + hmDirectionality.get(key) + "," + key);
		}

		strIDs.add(key);

		return absFeatureVec;
	}

	/*
	 * @functionality: primary function to process a collection of abstracts
	 * 
	 * @param: lim is the number of abstracts to be processed
	 * 
	 * @param: if negOnly is true then only process abstracts with
	 * Directionality=2
	 */
	private void processAbstracts(int lim, boolean negOnly) {

		Collection c = hmTell.keySet();

		Iterator it = c.iterator();

		int K = 0;

		String key = "";

		while (K < startPoint) {

			it.next();

			++K;
		}

		while ((it.hasNext()) && (K < startPoint + lim)) {

			key = it.next().toString();

			if ((!negOnly) || (key.indexOf("--- 2 ---") > -1)) {

				Stack stkTell = (Stack) hmTell.get(key);

				String tailind = "";

				if (hmTailInd.get(key) != null)
					tailind = hmTailInd.get(key).toString();

				getFeatureVectorAbstract(key, stkTell);

				// strTraining.add(key);

				++K;
			}
		}
	}

	/*
	 * @functionality: primary function to process a collection of abstracts
	 * 
	 * @param: lim is the number of abstracts to be processed
	 * 
	 * @param: if negOnly is true then only process abstracts with
	 * Directionality=2
	 */
	private void processAbstractsNew(int lim, boolean negOnly) {

		Collection c = hmTell.keySet();

		Iterator it = c.iterator();

		int K = 0;

		String key = "";

		while (K < startPoint) {

			it.next();

			++K;
		}

		while ((it.hasNext()) && (K < startPoint + lim)) {

			key = it.next().toString();

			Stack stkTell = (Stack) hmTell.get(key);

			// System.out.println(key);

			String dirn = hmDirectionality.get(key).toString();

			if (negOnly) {

				// System.out.println(K);

				if (dirn.equals("2")) {

					getFeatureVectorAbstract(key, stkTell);

					++K;
				}

			} else {

				getFeatureVectorAbstract(key, stkTell);

				++K;
			}

			// strTraining.add(key);

		}
	}

	/*
	 * @functionality: get the feature vector for a (tell) sentence
	 * 
	 * @param: sent is the (tell) sentence
	 * 
	 * @return: a string that is the feature vector
	 */

	private String getFeatureVectorSentence(String sent) {

		String featureVec = "";

		try {

			ffl.tagText(sent);
		} catch (Exception e) {
		}

		try {

			if (ffl.isNegatedSentence(sent)) {

				featureVec = featureVec + "T";
			} else {

				featureVec = featureVec + "F";
			}
		} catch (Exception e) {
			featureVec = featureVec + "F";
		}

		featureVec = featureVec + "\t";

		try {
			if (ffl.isNegatedIndicator(sent)) {

				featureVec = featureVec + "T";
			} else {

				featureVec = featureVec + "F";
			}
		} catch (Exception e) {
			featureVec = featureVec + "F";
		}

		featureVec = featureVec + "\t";

		try {

			if (ffl.isNegatedCause(sent)) {

				featureVec = featureVec + "T";
			} else {

				featureVec = featureVec + "F";
			}
		} catch (Exception e) {
			featureVec = featureVec + "F";
		}

		featureVec = featureVec + "\t";

		try {

			if (ffl.isTriggerSentence(sent)) {

				featureVec = featureVec + "T";
			} else {

				featureVec = featureVec + "F";
			}
		} catch (Exception e) {
			featureVec = featureVec + "F";
		}

		featureVec = featureVec + "\t";

		if (ffl.containsSpecLitagion(sent)) {

			featureVec = featureVec + "T";
		} else {

			featureVec = featureVec + "F";
		}

		featureVec = featureVec + "\t";

		if (ffl.containsSpecHarm(sent)) {

			featureVec = featureVec + "T";
		} else {

			featureVec = featureVec + "F";
		}

		featureVec = featureVec + "\t";

		Integer C = ffl.getLengthWords(sent);

		featureVec = featureVec + C;

		// System.out.println(featureVec);

		return featureVec;
	}

	/*
	 * 
	 */
	private boolean isSummary(String sentence) {

		sentence = sentence.toLowerCase().trim();

		String[] parts = sentence.split("[ ]");

		int S = parts.length;

		int A = stkBegin.size();

		for (int j = 0; j < A; ++j) {

			String pattern = stkBegin.get(j);

			if ((sentence.indexOf(pattern) > -1) && (sentence.indexOf(pattern) < 10))
				return true;
		}

		return false;
	}

	/*
	 * @functionality: get key i.e., PMID value from more extended string
	 * 
	 * @param: key is the extended string in which the PMID is embedded
	 * 
	 * @return: the actual PMID
	 */
	private String getKey(String key) {

		int ind = key.indexOf(" --- ");

		if (ind > 0) {

			key = key.substring(ind + 5);

			key = key.replaceAll("[^0-9]", "").trim();
		}

		if (key.equals("1")) {

			key = "T";
		} else {

			key = "F";
		}

		return key;
	}

	public void main(String[] args) {

		initialize();

		ffl.initialize();

		slhr.init();

		startPoint = 0;

		// processTell("TellResultsNe.txt");
		processTellNew("tell-sentences-4.txt");

		// System.exit(1);

		// processAbstracts(7,true);

		processAbstractsNew(200, false);

		processAbstractsNew(100, true);

		// processAbstracts(15,false);

		String trainingFname = "wekatr-newtell.csv";

		createTrainingFile(trainingFname);
	}

	/*
	 * @functionality: Create a CSV file of features and directionality that can
	 * be used by classifiers
	 */
	private void createTrainingFile(String fname) {

		strTraining.remove(0);

		String col = "";

		for (Integer I = 0; I < 55; ++I)
			col = col + "A" + I + ",";

		col = col.substring(0, col.length() - 1);

		if (startPoint == 0)
			strTraining.add(0, col);

		io.writeFile_BasicAppend(fname, strTraining);

		io.writeFile_Basic("IDs.csv", strIDs);
	}
}
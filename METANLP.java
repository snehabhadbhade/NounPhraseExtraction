package edu.umich.si;

/*
 * The code extracts the noun phrases from the text and then creates
 /* a topic vector
 */
import cc.mallet.pipe.CharSequence2TokenSequence;
import cc.mallet.pipe.CharSequenceLowercase;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.TokenSequence2FeatureSequence;
import cc.mallet.pipe.TokenSequenceRemoveStopwords;
import cc.mallet.pipe.iterator.StringArrayIterator;
import cc.mallet.topics.ParallelTopicModel;
import cc.mallet.topics.TopicInferencer;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.IDSorter;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.LabelSequence;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.Sentence;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONException;
//import org.json.simple.JSONArray;
import org.json.JSONObject;


/**
 *
 * @author snehabhadbhade
 */
public class METANLP extends HttpServlet {

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    
    public static final String NP = "NP";
    public static final int NUM_TOPICS = 5;
    StanfordCoreNLP pipeline;
    TregexPattern patternNP;
    Map<CoreMap,Integer> sentenceSentiment;
   
    public void init() {

        Properties props = new Properties();
        props.put("annotators", "tokenize, ssplit, pos, lemma, parse, sentiment");
        sentenceSentiment = new HashMap();
        pipeline = new StanfordCoreNLP(props);
        patternNP = TregexPattern.compile("NN|NNS");    
    }
   

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException 
    {
        JSONObject nlpOutput = new JSONObject();
        List<String> NounPhrases = new ArrayList<String>();
        ArrayList<ArrayList<String>> Topics = new ArrayList<ArrayList<String>>();
        String text = request.getParameter("stext");
        Annotation annotation;
        annotation = new Annotation(text);
        pipeline.annotate(annotation);
        analyze_sentiment(annotation, response, nlpOutput);
        NounPhrases = analyze_nounphrases(annotation, response);
        Topics = init_mallet(NounPhrases);
        response.setContentType("application/json"); 
        PrintWriter output = response.getWriter();
        try {
           
            nlpOutput.put("noun_phrases", NounPhrases); 
            nlpOutput.put("topics", Topics); 
        } catch (JSONException ex) {
            Logger.getLogger(METANLP.class.getName()).log(Level.SEVERE, null, ex);
        }
        output.println(nlpOutput);
        
    }
    
    public void analyze_sentiment(Annotation annotation, HttpServletResponse response,  JSONObject nlpOutput) throws ServletException, IOException 
    {
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter output = response.getWriter();
        try {
            for (CoreMap sentence : sentences) 
            {
                Tree x = sentence.get(TreeAnnotation.class);
                
                int sentiment = RNNCoreAnnotations.getPredictedClass(sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class));
                try {
                    
                    JSONObject sentenceOutput = new JSONObject();
                    sentenceOutput.put("sentence",sentence);
                    sentenceOutput.put("value",sentiment);
                    nlpOutput.append("sentiment",sentenceOutput);
                    
                    
                } catch (JSONException ex) {
                    Logger.getLogger(METANLP.class.getName()).log(Level.SEVERE, null, ex);
                }
               
            }  
        } finally{}
    }
    
   List<String> analyze_nounphrases(Annotation annotation, HttpServletResponse response) throws ServletException, IOException 
    {
        String nounPhrase;
        List<String> NounPhrases = new ArrayList<String>();
        List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
        try {
            for (CoreMap sentence : sentences) 
            {
                Tree x = sentence.get(TreeAnnotation.class);
                
                TregexMatcher matcher = patternNP.matcher(x);
                while (matcher.findNextMatchingNode()) 
                {
                    Tree match = matcher.getMatch();
                    nounPhrase = Sentence.listToString(match.yield());
                    if (!NounPhrases.contains(nounPhrase))
                            {
                    NounPhrases.add(Sentence.listToString(match.yield()));
                            }
                }
                
            }
        } finally{ 
            
        }
        return NounPhrases;
    }
    
    

    ArrayList<ArrayList<String>> init_mallet(List <String>NounPhrases) throws IOException 
    {
        String[] Noun_phrases = new String[NounPhrases.size()];
        NounPhrases.toArray(Noun_phrases);
        InstanceList instances;
        ArrayList<Pipe> pipeList;
        pipeList = new ArrayList<Pipe>();
        pipeList.add( new CharSequenceLowercase() );
        pipeList.add( new CharSequence2TokenSequence(Pattern.compile("\\p{L}[\\p{L}\\p{P}]+\\p{L}")) );
        pipeList.add( new TokenSequence2FeatureSequence() );
        instances = new InstanceList (new SerialPipes(pipeList));
        instances.addThruPipe(new StringArrayIterator(Noun_phrases)); // data, label, name fields
        ParallelTopicModel model = new ParallelTopicModel(NUM_TOPICS, 1.0, 0.01);
        model.addInstances(instances);
        model.setNumThreads(2);
        model.setNumIterations(3);
        model.estimate();
        
        Alphabet dataAlphabet = instances.getDataAlphabet();
        
        double[] topicDistribution = model.getTopicProbabilities(0);
        ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
        ArrayList<ArrayList<String>> Topics;
        Topics = new ArrayList<ArrayList<String>>();
		
		// Show top 5 words in topics with proportions for the first document
	for (int topic = 0; topic < NUM_TOPICS; topic++) 
        {
	Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
        ArrayList topic_words = new ArrayList<String>();
        //topics.add(topic);
        topic_words.add(topicDistribution[topic]);
       int rank = 0;
	while (iterator.hasNext() && rank < 2) 
        {
            
            IDSorter idCountPair = iterator.next();
            topic_words.add(dataAlphabet.lookupObject(idCountPair.getID())); 
            topic_words.add((idCountPair.getWeight()));
	    rank++;
	}
	Topics.add(topic_words);
	}
        /*
           
        List<String> Topics = new ArrayList<String>();
        String topicName;
        
        //ArrayList<TreeSet<IDSorter>> topicSortedWords = model.getSortedWords();
       
        for (int topic = 0; topic < NUM_TOPICS; topic++) {
        Iterator<IDSorter> iterator = topicSortedWords.get(topic).iterator();
        
        int rank = 0;
        while (iterator.hasNext() && rank < 5) {
        IDSorter idCountPair = iterator.next();
        topicName = (String) dataAlphabet.lookupObject(idCountPair.getID());
        if(!Topics.contains(topicName))
        {
        Topics.add(topicName);
        }
        
        rank++;
        }
       
        }
        */
        
      return Topics;  
    
    }
    

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}

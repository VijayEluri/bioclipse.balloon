/*******************************************************************************
 * Copyright (c) 2009 Ola Spjuth.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Ola Spjuth - initial API and implementation
 ******************************************************************************/
package net.bioclipse.balloon.business;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;

import net.bioclipse.balloon.runner.BalloonRunner;
import net.bioclipse.core.ResourcePathTransformer;
import net.bioclipse.core.business.BioclipseException;

/**
 * A Bioclipse Manager for invoking Balloon
 * (http://web.abo.fi/~mivainio/balloon/index.php) Fromm Balloon homepage:
 * Balloon creates 3D atomic coordinates from molecular connectivity via
 * distance geometry and confomer ensembles using a multi-objective genetic
 * algorithm. The input can be SMILES, SDF or MOL2 format. Output is SDF or
 * MOL2. Flexibility of aliphatic rings and stereochemistry about double bonds
 * and tetrahedral chiral atoms is handled.
 * 
 * @author ola
 */
public class BalloonManager implements IBalloonManager {

    private static final Logger logger =Logger.getLogger( BalloonManager.class );

    private static List<String> supportedContentTypes;
    
    /**
     * Defines the Bioclipse namespace for balloon.
     * Appears in the scripting language as the namespace/prefix
     */
    public String getNamespace() {
        return "balloon";
    }


    /**
     * Generate 3D for a single file
     */
    public String generate3Dcoordinates( String inputfile ) throws BioclipseException {
        return generate3Dconformations( inputfile, 1 );
    }

    /**
     * Generate 3D for a list of files
     */
    public List<String> generate3Dcoordinates( List<String> inputfiles ) throws BioclipseException {
        return generate3Dconformations( inputfiles, 1 );
    }

    /**
     * Generate 3D for a single file with ouput file specified
     */
    public String generate3Dcoordinates( String inputfile, String outputfile ) throws BioclipseException {
        return generate3Dconformations( inputfile, outputfile, 1 );
    }

    /**
     * Generate n 3D conformations for a single file
     */
    public String generate3Dconformations( String inputfile , int numConformations) throws BioclipseException {
        return generate3Dconformations( inputfile, null,numConformations );
    }

    /**
     * Generate n 3D conformations for a list of files
     */
    public List<String> generate3Dconformations( List<String> inputfiles,
                                                 int numConformations ) throws BioclipseException {

          List<String> outputfiles = new ArrayList<String>();
          for ( String inputfile : inputfiles ) {
              String ret = generate3Dconformations( inputfile , numConformations);
              outputfiles.add( ret );
          }
          return outputfiles;
      }

    /**
     * Generate a number of 3D conformations for a file with one or more
     * chemical structures.
     * 
     * @param inputfile The inputfile with the existing structures
     * @param outputfile Outputfile to write, will be SDF if more than one mol
     * @param numConformations Number of conformations to generate
     */
    public String generate3Dconformations( String inputfile, String outputfile,
                                           int numConformations ) throws BioclipseException {
        
        if (supportedContentTypes==null)
            fillSupportedContentTypes();

        //Must have different input as output files
        if (inputfile.equals( outputfile )) 
            throw new IllegalArgumentException("Outputfile must be different " +
            		"from inputfile for Balloon ");
        
        IFile inIfile=ResourcePathTransformer.getInstance().transform( inputfile );
        String infile=inIfile.getRawLocation().toOSString();

        IContentDescription condesc=null;
        try {
            condesc = inIfile.getContentDescription();
        } catch ( CoreException e ) {
            throw new BioclipseException("The file " + inputfile + 
            " has unknown contenttype: " + e.getMessage()); 
        }

        if (condesc==null)
            throw new BioclipseException("The file " + inputfile + 
                                         " has no contenttype and is hence not " +
                                         "supported for ballloon"); 
            
        //Verify content types
        if (!isSupportedContenttype(condesc))
                throw new BioclipseException("The file " + inputfile + 
                       " has content type: " + 
                       condesc.getContentType().getName() + 
                       " which is not supported by balloon.");

        String outfile="";
        if (outputfile==null){
            outfile=constructOutputFilename( infile, numConformations );
        }else{
            IFile outIfile=ResourcePathTransformer.getInstance().transform( outputfile );
            outfile=outIfile.getRawLocation().toOSString();
        }

        logger.debug( "Infile transformed to: " + infile);
        logger.debug( "Outfile transformed to: " + outfile);
        
        try {
            //Create a native runner and execute Balloon with it
            //writing from inputfile to outputfile with desired number of conformations
            BalloonRunner runner=new BalloonRunner(new Long(20000));
            boolean status=runner.runBalloon( infile,outfile,
                                                 numConformations );
            if (!status){
                throw new BioclipseException("Balloon execution failed. Native BalloonRunner returned false." );
            }
        } catch ( ExecutionException e ) {
            throw new BioclipseException("Balloon execution failed. Reason: " + e.getMessage());
        } catch ( InterruptedException e ) {
            throw new BioclipseException("Balloon Was interrupted. Reason: " + e.getMessage());
        } catch ( TimeoutException e ) {
            throw new BioclipseException("Balloon timed out. Reason: " + e.getMessage());
        } catch ( IOException e ) {
            throw new BioclipseException("Balloon I/O error. Reason: " + e.getMessage());
        }
        
        logger.debug("Balloon run successful, wrote file: " + outfile);
        return outfile;

    }



    private void fillSupportedContentTypes() {

        //These entries need to match the command in plugin.xml but found
        //no easy way to read this info from there
        supportedContentTypes=new ArrayList<String>();
        supportedContentTypes.add( "net.bioclipse.contenttypes.sdf" );
        supportedContentTypes.add( "net.bioclipse.contenttypes.mdlMolFile2D" );
        supportedContentTypes.add( "net.bioclipse.contenttypes.mdlMolFile3D" );
        
    }


    private boolean isSupportedContenttype(IContentDescription condesc) {

        for (String supCon : supportedContentTypes){
            IContentType testType = Platform.getContentTypeManager()
                .getContentType( supCon );
            
            if (testType!=null && condesc.getContentType().isKindOf( testType ))
                return true;
            
        }

        return false;
    }


    /**
     * Helper method to construct output filename from inputfilename +_3d
     * @param inputfile
     * @param numConformations
     * @return
     */
    private String constructOutputFilename(String inputfile, int numConformations){

        int lastpathsep=inputfile.lastIndexOf( File.separator );
        String path=inputfile.substring( 0, lastpathsep );
        String name=inputfile.substring( lastpathsep+1, inputfile.length()-4 );
        String currentExtension=inputfile.substring( inputfile.length()-4, inputfile.length() );

        String ext="";
        if (numConformations>1) ext=".sdf";
        else ext=currentExtension;

        String pathfile=path + File.separator + name;

        int cnt=1;
        String outfile=getAFilename( pathfile, ext, cnt );
        File file=new File(outfile);
        while(file.exists()){
            cnt++;
            outfile=getAFilename( pathfile, ext, cnt );
            file=new File(outfile);
        }
        return outfile;
    }

    /**
     * Increment file numbering to get unique file
     * @param pathname
     * @param ext
     * @param cnt
     * @return
     */
    private String getAFilename(String pathname, String ext, int cnt){

        if (cnt<=1)
            return pathname+"_3d" + ext;
        else
            return pathname+"_3d_" + cnt + ext;

    }

}

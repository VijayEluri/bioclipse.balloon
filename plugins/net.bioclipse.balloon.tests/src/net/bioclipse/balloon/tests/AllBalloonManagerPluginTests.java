package net.bioclipse.balloon.tests;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(value=Suite.class)
@SuiteClasses( {
	JavaBalloonManagerPluginTest.class,
	JavaScriptBalloonManagerPluginTest.class
})
public class AllBalloonManagerPluginTests {

}

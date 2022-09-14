component extends="Appender" {
	
    fields=[

		field("DSN","dsn","",false,"Sentry Data Source Name (DSN). This setting is optional and can also be set in the enviroment instead (https://docs.sentry.io/clients/java/config/##configuration-methods).","text")
		

		,field(
			"Environment","environment",
			"",
			false,
			"Refers to your code deployment naming convention. For example, development, testing, staging and so on.",
			"text")

		,field(
			"Dist","dist",
			"",
			false,
			"Distinguishes build or deployment variants of the same release of an application. For example, the dist can be the build number of an XCode build or the version code of an Android build.",
			"text")
		,field(
			"Tags","tags",
			"",
			false,
			"Tags are various key/value pairs that get assigned to an event, and you can use them later as a breakdown or quick access to finding related events. 
			<br><br>Please separate the pairs with a comma and use ""|"" as separator between key and value like this:<br> myKey1|my value 1,myKey2|my value 2",
			"textarea")		
			,field(
				"Extras","extras",
				"",
				false,
				"These fields are unsearchable, they enrich your events with information that will help you debug and resolve the associated issues.
				<br><br>Please separate the pairs with a comma and use ""|"" as separator between key and value like this:<br> myKey1|my value 1,myKey2|my value 2",
				"textarea")
			,field(
				"Debug","debug",
				"true",
				false,
				"Enable debugging, if enabled it will show debug information in the console",
				"checkbox")
	];
	
	public array function getCustomFields() {
		if(!isNull(form._name)) {
			var fields=duplicate(variables.fields);
			loop array=fields index="local.i" item="local.field" {
				if(field.getName() EQ "Path") {
					local.dv=field.getDefaultValue();
					if(right(dv,1) NEQ "/" and right(dv,1) NEQ "\") {
						dv&="/";
					}
					field.setDefaultValue(dv&form._name&".log");
				}
			}
		}
		return fields;
	}
    
    public string function getClass() {
		return "{class}";
    }
    
    public string function getBundleName() {
		return "{bundlename}";
    }
    
    /*public string function getBundleVersion() {
		return "{bundleversion}";
    }*/
    
    public string function getLabel() {
		return "{label}";
    }
    
    public string function getDescription() {
		return "{desc}";
    }
}
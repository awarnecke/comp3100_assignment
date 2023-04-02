OBJS = build/SimClient.class build/SimHelper.class build/Job.class build/Resources.class build/Server.class
.PHONY: all clean install
all: $(OBJS)

JAVAC ?= javac
PREFIX ?= ..

build:
	@echo " [MKDIR] build"
	@mkdir -p build

build/%.class: src/%.java build
	@echo " [JAVAC] $<"
	@$(JAVAC) -d build -cp src "$<"

clean:
	@echo " [CLEAN]"
	@rm -rf build

install: $(OBJS)
	@echo " [COPY]  $(PREFIX)"
	@cp $(OBJS) $(PREFIX)

from lark import Lark, Transformer

# Load the grammar from a .txt file
with open('testingGrammer.txt', 'r') as file:
    grammar = file.read()

# Create a Lark parser using the loaded grammar
parser = Lark(grammar, start='start')

# Define a transformer to handle the BODMAS operations
class EvalBODMAS(Transformer):
    def add(self, items):
        print(f"Adding: {items[0]} + {items[1]}")  # Debug print
        return float(items[0]) + float(items[1])

    def subtract(self, items):
        print(f"Subtracting: {items[0]} - {items[1]}")  # Debug print
        return float(items[0]) - float(items[1])

    def multiply(self, items):
        print(f"Multiplying: {items[0]} * {items[1]}")  # Debug print
        return float(items[0]) * float(items[1])

    def divide(self, items):
        print(f"Dividing: {items[0]} / {items[1]}")  # Debug print
        return float(items[0]) / float(items[1])

    def group(self, items):
        print(f"Grouping: {items[0]}")  # Debug print
        return items[0]  # Return the result of the expression inside parentheses

    def NUMBER(self, n):
        return float(n[0])  # Convert the string number to a float

# Example of a large equation
equation = "3 + 5 * (10 - 2) / 4 * (6 + 2) - 8"

# Parse the equation
tree = parser.parse(equation)

# Print the parse tree
print("Parse Tree:")
print(tree.pretty())  # Display the parse tree

# Now perform the transformation and evaluate the result
result = EvalBODMAS().transform(tree)
print("Result:", result)  # Outputs the result after applying BODMAS rules
